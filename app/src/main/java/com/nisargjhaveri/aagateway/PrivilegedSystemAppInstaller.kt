package com.nisargjhaveri.aagateway

import android.content.Context
import androidx.core.content.pm.PackageInfoCompat
import java.io.File
import java.io.IOException
import java.util.concurrent.TimeUnit
import java.util.zip.ZipEntry
import java.util.zip.ZipOutputStream

class PrivilegedSystemAppInstaller(context: Context) {
    data class Result(
        val success: Boolean,
        val message: String,
    )

    private val context = context.applicationContext

    fun install(): Result {
        val sourceApk = File(context.applicationInfo.sourceDir)
        if (!sourceApk.isFile) {
            return Result(false, "The installed app package could not be found.")
        }

        val workspace = File(context.cacheDir, "${MODULE_ID}_installer")
        if (workspace.exists() && !workspace.deleteRecursively()) {
            return Result(false, "The previous installation files could not be removed.")
        }

        return try {
            val moduleDirectory = File(workspace, "module")
            val moduleArchive = File(workspace, "$MODULE_ID.zip")
            val packageInfo =
                context.packageManager.getPackageInfo(context.packageName, 0)
            MagiskModuleBuilder.build(
                moduleDirectory,
                sourceApk,
                packageInfo.versionName.orEmpty(),
                PackageInfoCompat.getLongVersionCode(packageInfo),
            )
            MagiskModuleBuilder.archive(moduleDirectory, moduleArchive)
            installModule(moduleArchive)
        } catch (exception: IOException) {
            Result(false, exception.message ?: "The Magisk module could not be prepared.")
        } finally {
            workspace.deleteRecursively()
        }
    }

    fun remove(): Result {
        val modulePath = shellQuote(MODULE_PATH)
        val command =
            """
            set -eu
            test "$(id -u)" = "0" || { echo "Root access was not granted."; exit 10; }
            test -d $modulePath || {
                echo "The AA Wireless Gateway Magisk module is not installed."
                exit 12
            }
            touch $modulePath/remove
            sync
            """.trimIndent()

        val rootResult = runRootCommand(command)
        return if (rootResult.exitCode == 0) {
            Result(
                true,
                "Removal is ready. Restart the phone to return the app to normal installation.",
            )
        } else {
            Result(
                false,
                rootResult.output.ifBlank {
                    "Removal failed with root exit code ${rootResult.exitCode}."
                },
            )
        }
    }

    fun reboot(): Result {
        val rootResult =
            runRootCommand(
                """
                test "$(id -u)" = "0" || {
                    echo "Root access was not granted."
                    exit 10
                }
                reboot
                """.trimIndent()
            )
        return if (rootResult.exitCode == 0) {
            Result(true, "Restarting the phone.")
        } else {
            Result(
                false,
                rootResult.output.ifBlank {
                    "The phone could not be restarted with root access."
                },
            )
        }
    }

    fun setGatewayPowerProfile(enabled: Boolean): Result {
        val profile = if (enabled) "true" else "false"
        val rootResult =
            runRootCommand(
                """
                set -eu
                test "$(id -u)" = "0" || {
                    echo "Root access was not granted."
                    exit 10
                }
                command -v cmd >/dev/null 2>&1 || {
                    echo "The Android power profile command is unavailable."
                    exit 14
                }
                cmd power set-fixed-performance-mode-enabled $profile
                """.trimIndent()
            )
        return if (rootResult.exitCode == 0) {
            Result(
                true,
                if (enabled) {
                    "Fixed-performance mode enabled."
                } else {
                    "Fixed-performance mode disabled."
                },
            )
        } else {
            Result(
                false,
                rootResult.output.ifBlank {
                    "This Android version does not support the reversible power profile."
                },
            )
        }
    }

    private fun installModule(moduleArchive: File): Result {
        val archivePath = shellQuote(moduleArchive.absolutePath)
        val command =
            """
            set -eu
            test "$(id -u)" = "0" || { echo "Root access was not granted."; exit 10; }
            command -v magisk >/dev/null 2>&1 || {
                echo "Magisk was not found on this rooted device."
                exit 11
            }
            magisk --install-module $archivePath
            test -d ${shellQuote(MODULE_PATH)} || {
                echo "Magisk did not install the privileged module."
                exit 13
            }
            sync
            """.trimIndent()

        val rootResult = runRootCommand(command)
        return if (rootResult.exitCode == 0) {
            Result(
                true,
                "Privileged installation is ready. Restart the phone to activate USB access.",
            )
        } else {
            Result(
                false,
                rootResult.output.ifBlank {
                    "Installation failed with root exit code ${rootResult.exitCode}."
                },
            )
        }
    }

    private fun runRootCommand(command: String): RootCommandResult {
        val process =
            try {
                ProcessBuilder("su", "-c", command).redirectErrorStream(true).start()
            } catch (exception: IOException) {
                return RootCommandResult(-1, "Root access is unavailable: ${exception.message}")
            }

        if (!process.waitFor(ROOT_TIMEOUT_SECONDS, TimeUnit.SECONDS)) {
            process.destroyForcibly()
            return RootCommandResult(-1, "The root request timed out.")
        }

        return RootCommandResult(
            process.exitValue(),
            process.inputStream.bufferedReader().use { it.readText() }.trim(),
        )
    }

    private data class RootCommandResult(
        val exitCode: Int,
        val output: String,
    )

    companion object {
        const val MODULE_ID = "aawirelessgateway_system"
        const val PREFERENCE_INSTALL_TRANSITION = "privileged_install_transition"
        const val INSTALL_PENDING = "install_pending"
        const val REMOVAL_PENDING = "removal_pending"
        private const val MODULE_PATH = "/data/adb/modules/$MODULE_ID"
        private const val ROOT_TIMEOUT_SECONDS = 60L
    }
}

object MagiskModuleBuilder {
    fun build(
        moduleDirectory: File,
        sourceApk: File,
        versionName: String,
        versionCode: Long,
    ) {
        val appDirectory =
            File(moduleDirectory, "system/priv-app/AAWirelessGateway").also {
                if (!it.mkdirs()) {
                    throw IOException("The privileged app directory could not be created.")
                }
            }
        sourceApk.copyTo(File(appDirectory, "AAWirelessGateway.apk"), overwrite = true)

        File(moduleDirectory, "module.prop")
            .writeText(
                """
                id=${PrivilegedSystemAppInstaller.MODULE_ID}
                name=AA Wireless Gateway privileged access
                version=$versionName
                versionCode=$versionCode
                author=AA Wireless Gateway
                description=Installs AA Wireless Gateway as a privileged system app
                """.trimIndent() + "\n"
            )

        val permissionsDirectory =
            File(moduleDirectory, "system/etc/permissions").also {
                if (!it.mkdirs()) {
                    throw IOException("The privileged permissions directory could not be created.")
                }
            }
        File(
            permissionsDirectory,
            "privapp-permissions-com.nisargjhaveri.aagateway.xml",
        ).writeText(
            """
            <?xml version="1.0" encoding="utf-8"?>
            <permissions>
                <privapp-permissions package="com.nisargjhaveri.aagateway">
                    <permission name="android.permission.MANAGE_USB" />
                    <permission name="android.permission.TETHER_PRIVILEGED" />
                </privapp-permissions>
            </permissions>
            """.trimIndent() + "\n"
        )
    }

    fun archive(moduleDirectory: File, archive: File) {
        archive.parentFile?.mkdirs()
        ZipOutputStream(archive.outputStream().buffered()).use { output ->
            moduleDirectory
                .walkTopDown()
                .filter(File::isFile)
                .sortedBy { it.relativeTo(moduleDirectory).invariantSeparatorsPath }
                .forEach { file ->
                    output.putNextEntry(
                        ZipEntry(file.relativeTo(moduleDirectory).invariantSeparatorsPath)
                    )
                    file.inputStream().use { it.copyTo(output) }
                    output.closeEntry()
                }
        }
    }
}

fun shellQuote(value: String): String {
    return "'${value.replace("'", "'\"'\"'")}'"
}
