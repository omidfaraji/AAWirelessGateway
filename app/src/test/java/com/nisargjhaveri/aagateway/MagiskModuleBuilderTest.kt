package com.nisargjhaveri.aagateway

import java.io.File
import org.junit.Assert.assertArrayEquals
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TemporaryFolder
import java.util.zip.ZipFile

class MagiskModuleBuilderTest {
    @get:Rule val temporaryFolder = TemporaryFolder()

    @Test
    fun buildsPrivilegedAppModule() {
        val sourceApk =
            temporaryFolder.newFile("source.apk").also {
                it.writeBytes(byteArrayOf(1, 2, 3))
            }
        val moduleDirectory = File(temporaryFolder.root, "module")

        MagiskModuleBuilder.build(
            moduleDirectory,
            sourceApk,
            versionName = "1.3.0",
            versionCode = 29,
        )

        assertArrayEquals(
            sourceApk.readBytes(),
            File(
                    moduleDirectory,
                    "system/priv-app/AAWirelessGateway/AAWirelessGateway.apk",
                )
                .readBytes(),
        )
        assertTrue(
            File(moduleDirectory, "module.prop")
                .readText()
                .contains("id=aawirelessgateway_system")
        )
        val permissions =
            File(
                    moduleDirectory,
                    "system/etc/permissions/" +
                        "privapp-permissions-com.nisargjhaveri.aagateway.xml",
                )
                .readText()
        assertTrue(permissions.contains("android.permission.MANAGE_USB"))
        assertTrue(permissions.contains("android.permission.TETHER_PRIVILEGED"))

        val archive = File(temporaryFolder.root, "module.zip")
        MagiskModuleBuilder.archive(moduleDirectory, archive)

        ZipFile(archive).use { zip ->
            assertTrue(zip.getEntry("module.prop") != null)
            assertTrue(
                zip.getEntry("system/priv-app/AAWirelessGateway/AAWirelessGateway.apk") != null
            )
            assertTrue(
                zip.getEntry(
                    "system/etc/permissions/" +
                        "privapp-permissions-com.nisargjhaveri.aagateway.xml"
                ) != null
            )
        }
    }

    @Test
    fun quotesShellPaths() {
        assertTrue(shellQuote("/data/user/0/app's cache") == "'/data/user/0/app'\"'\"'s cache'")
    }
}
