package modder

import apktool.kotlin.lib.Apktool
import net.lingala.zip4j.ZipFile
import org.apache.commons.lang3.StringUtils
import java.io.File
import java.io.IOException
import java.io.PrintWriter
import java.nio.charset.Charset
import java.nio.file.Files
import java.nio.file.Paths
import kotlin.io.path.Path

// TODO: add a new class to Patcher for specific patch like adding a mem scanner
// called MemScanner
class Patcher(
        apkFilePathStr: String,
        cleanDecompilationOnExit: Boolean = true,
        val decodeResource: Boolean,
) {
    var apkFilePathStr: String
    val resource = Resource()
    val apktool: Apktool

    // ===================
    init {
        val apkFile = File(apkFilePathStr)
        Assert.AssertExistAndIsFile(apkFile)
        // make sure to get the absolute path
        // https://stackoverflow.com/a/17552395/14073678
        this.apkFilePathStr = apkFile.absolutePath
        // =============================== decompile the apk ===========
        apktool = Apktool(
                apkFile = apkFilePathStr,
                decodeResource = decodeResource,
                // TODO: temporary solution to clean up
                // in the future should use Closeable.use {}
                decompilationFolder = TempManager.CreateTempDirectory("ModderDecompiledApk", cleanDecompilationOnExit).toFile(),
        )
        logger.info { "decompiled at ${apktool.decompilationFolder}" }
    }

    // TODO: find a way to cut down code duplication between this function and
    // GetEntrySmaliPath
    fun GetSmaliFolderOfLaunchableActvity(): String {

        // find launchable activity
        val launchableActivity = Aapt.GetLaunchableActivity(apkFilePathStr)
        // just exit if can't get a launchable activity
        if (StringUtils.isEmpty(launchableActivity)) {
            val errMsg = String.format("Cannot find launchable activity from apk %s", apkFilePathStr)
            throw RuntimeException(errMsg)
        }
        val relativeSmaliFilePath = LaunchableActivityToSmaliRelativePath(launchableActivity)
        //

        // when decompiling with apktool
        // the smali classes in subPath will be contained in
        // the folder starting with smali
        // like smali, smali_classes2, smali_classes3 and ect
        val decompiledApkDir = apktool.decompilationFolder!!
        val files = decompiledApkDir.listFiles()!!
        for (i in files.indices) {
            if (!files[i].isDirectory) continue
            val dirName = files[i].name
            if (!dirName.startsWith("smali")) continue
            val basePathStr = files[i].absolutePath
            val smaliFile = File(basePathStr, relativeSmaliFilePath)
            // check if this thing actually exist
            if (smaliFile.exists()) return basePathStr
        }
        return ""
    }

    fun GetEntrySmaliPath(): String {

        // find launchable activity
        val launchableActivity = Aapt.GetLaunchableActivity(apkFilePathStr)
        // just exit if can't get a launchable activity
        if (StringUtils.isEmpty(launchableActivity)) {
            val errMsg = String.format("Cannot find launchable activity from apk %s", apkFilePathStr)
            throw RuntimeException(errMsg)
        }
        val relativeSmaliFilePath = LaunchableActivityToSmaliRelativePath(launchableActivity)
        val basePathStr = GetSmaliFolderOfLaunchableActvity()
        return if (StringUtils.isEmpty(basePathStr)) "" else File(basePathStr, relativeSmaliFilePath).absolutePath
    }

    fun GetNativeLibSupportedArch(): Array<String> {
        val apkNativeLibDir = File(apktool.decompilationFolder, NATIVE_LIB_DIR_NAME)
        // check if the apk already have a native lib for some or all architecture
        // if the apk already has native lib for specific arch like "armeabi-v7a"
        // then we shouldn't add a new folder for another arch like arm-64
        // because the apk will choose the arm-64 one and it may not contains
        // the needed library from "armeabi-v7a"
        if (!apkNativeLibDir.exists()) return arrayOf()
        else return apkNativeLibDir.list { current, name -> File(current, name).isDirectory }!!
    }

    fun GetNativeLibSupportedArchCount(): Int {
        return GetNativeLibSupportedArch().size
    }

    /*
     * Create native lib directory for all architecture
     * if they previously doesn't exist
     *
     * returns path to that native library
     */
    fun CreateNativeLibDir(): String {
        // check first if apk already has a native lib
        val apkNativeLibDir = File(apktool.decompilationFolder, NATIVE_LIB_DIR_NAME)
        // no native lib directory found, make one
        if (!apkNativeLibDir.exists()) apkNativeLibDir.mkdirs()

        // check if the apk already have a native lib for some or all architecture
        // if the apk already has native lib for specific arch like "armeabi-v7a"
        // then we shouldn't add a new folder for another arch like arm-64
        // because the apk will choose the arm-64 one and it may not contains
        // the needed library from "armeabi-v7a"
        val archs = apkNativeLibDir.list { current, name -> File(current, name).isDirectory }!!
        // don't add new arch folder, just return
        if (archs.size > 0) return apkNativeLibDir.absolutePath
        // otherwise add native lib folder for each arch
        for (arch in ARCHS) {
            val archLibFolder = File(apkNativeLibDir.absolutePath, arch)
            if (!archLibFolder.exists()) {
                archLibFolder.mkdirs()
            }
        }
        return apkNativeLibDir.absolutePath
    }


    fun IterateNativeLibArchDir(onIter: (arch: String, archLibFolder: File) -> Unit) {
        // make sure to create directory for native libs
        val apkNativeLibDir = CreateNativeLibDir()
        val supportedArch = GetNativeLibSupportedArch()
        for (arch in supportedArch) {
            val archLibFolder = File(apkNativeLibDir, arch)
            // call callback
            onIter(arch, archLibFolder)
        }
    }


    fun AddFileToNativeLibDir(srcFileStr: String) {
        val srcFile = File(srcFileStr)
        if (!srcFile.exists()) {
            throw IOException(String.format("%s doesn't exist",
                    srcFileStr))
        }
        if (!srcFile.isFile) {
            throw IOException(String.format("%s is not a file",
                    srcFileStr))
        }
        IterateNativeLibArchDir { arch: String, archLibFolder: File ->
            // file should be added to /[decompiledApkDirStr]/lib/[arch]/libraryName
            val addedFile = File(archLibFolder.absolutePath,
                    srcFile.name)
            // lib file already exist, cannot add anymore
            if (addedFile.exists()) {
                val errMsg = String.format(
                        "Cannot add native library because %s already exist at directory %s",
                        srcFileStr,
                        archLibFolder.absolutePath)
                throw IOException(errMsg)
            }
            // copy the lib file
            Files.copy(srcFile.toPath(), addedFile.toPath())
        }
    }


    fun AddMemScannerLib() {
        // TODO: add test for apk that support one arch only
        IterateNativeLibArchDir { arch: String, archLibFolder: File ->
            val destFile = File(archLibFolder.absolutePath, MEM_SCANNER_LIB_NAME)
            // String srcFile = Paths.get(MEM_SCANNER_LIB_RESOURCE_DIR, arch,
            // MEM_SCANNER_LIB_NAME)
            // .toAbsolutePath()
            // .toString();
            val srcFile = java.lang.String.join("/", MEM_SCANNER_LIB_RESOURCE_DIR, arch, MEM_SCANNER_LIB_NAME)
            // lib file already exist, cannot add anymore
            if (destFile.exists()) {
                val errMsg = String.format(
                        "Cannot add native library because %s already exist at directory %s",
                        destFile.absolutePath,
                        archLibFolder.absolutePath)
                throw IOException(errMsg)
            }
            // copy the lib file
            resource.CopyResourceFile(srcFile, destFile.toString())
        }
    }


    fun DoesNativeLibExist(libName: String): Boolean {

        // need to use wrapper to accsess variable
        // from inside lambda
        var libExistInAllArch = true
        IterateNativeLibArchDir { arch: String, archLibFolder: File ->
            val libFile = File(archLibFolder.absolutePath, libName)
            if (!libFile.exists()) libExistInAllArch = false
        }
        return libExistInAllArch
    }


    fun GetPackageNameOfLaunchableActivity(): String {

        // find launchable activity
        val launchableActivity = Aapt.GetLaunchableActivity(apkFilePathStr)
        // just exit if can't get a launchable activity
        if (StringUtils.isEmpty(launchableActivity)) {
            val errMsg = String.format("Cannot find launchable activity from apk %s", apkFilePathStr)
            throw RuntimeException(errMsg)
        }

        // because split takes a regex string
        // to actually split by '.' we need to escape it first
        return launchableActivity.split("\\.".toRegex()).dropLastWhile { it.isEmpty() }.toTypedArray<String>()[0]
    }


    fun GetPackageDirOfLaunchableActivity(): String {
        val packageName = GetPackageNameOfLaunchableActivity()
        val smaliBaseDir = GetSmaliFolderOfLaunchableActvity()
        val smaliCodePackageDir = File(smaliBaseDir, packageName)
        return smaliCodePackageDir.absolutePath
    }

    fun AddMemScannerSmaliCode() {

        /**
         * copy the zip code of smali constructor from resources
         * to a temp folder and then extract to the apk
         */

        // copy zip to temp folder
        val tempDir: String = TempManager.CreateTempDirectory("TempSmalifolder").toString()
        val destSmaliZipCode = File(tempDir, MEM_SCANNER_SMALI_ZIP_NAME)
        resource.CopyResourceFile(MEM_SCANNER_SMALI_CODE_ZIP_PATH, destSmaliZipCode.absolutePath)

        /**
         * create new smali folder (new dex basically) to put our smali code
         * can't just use existing smali folder in order to mitigate dex limitation of 65536 max method
         * https://developer.android.com/build/multidex#:~:text=About%20the%2064K%20reference%20limit,-Android%20app%20(APK&text=The%20Dalvik%20Executable%20specification%20limits,methods%20in%20your%20own%20code.
         * https://github.com/iBotPeaches/Apktool/issues/2496
         * */
        //
        //
        val apkSmaliClassCount = apktool.GetSmaliClassesCount()
        val destRootDir = Path(apktool.decompilationFolder!!.toString(), "smali_classes${apkSmaliClassCount + 1}", "com").toFile()
        assert(destRootDir.mkdirs() == true)
        val destDir = File(destRootDir, MEM_SCANNER_SMALI_DIR_NAME).absolutePath
        ZipFile(destSmaliZipCode.absolutePath).use { zipFile: ZipFile ->
            zipFile.extractAll(destDir)
            zipFile.close()
        }
        logger.info { "extracted to ${destDir}" }
    }


    fun AddMemScanner() {
        AddMemScannerLib()
        AddMemScannerSmaliCode()

        // add constructor to start the memory scanner
        // server to the init function of smali launchable file
        val entrySmaliPathStr = GetEntrySmaliPath()
        val entrySmaliPath = Paths.get(entrySmaliPathStr)
        logger.info { "entry smali file: ${entrySmaliPathStr}" }
        val modifiedSmaliCode = AddMemScannerConstructorSmaliCode(entrySmaliPathStr)
        //logger.info { "========== modified smali code ========================" }
        //logger.info { modifiedSmaliCode.joinToString(separator = "\n") }
        //logger.info { "==================================================" }
        // rewrite file
        Files.write(entrySmaliPath, modifiedSmaliCode)
    }

    fun AddSupportForFreeInAppPurchases() {
        InAppPurchaseUtil.patchApk(apktool = apktool)
    }

    fun RemoveExtractNativeLibOptions() {
        if (!decodeResource) {
            throw IllegalStateException("Cannot remove extract native lib options when [decodeResource] is false")
        }
        val manifestFile = apktool.manifestFile
        val manifestContent = Files.readString(manifestFile.toPath())
        // remove the options all toget
        val newManifestContent = manifestContent.replace("android:extractNativeLibs=\"false\"", "")
        // warning if nothing is removed
        if (manifestContent === newManifestContent) {
            println("extractNativeLibs options not found")
        }
        PrintWriter(manifestFile.absolutePath).use { out -> out.println(newManifestContent) }
    }


    fun Export(exportPath: String) {
        val exportFile = File(exportPath)
        apktool.export(apkOutFile = exportPath, signApk = false)
        System.out.printf("exported to %s\n", exportFile.absolutePath)
    }

    companion object {
        val ARCHS = arrayOf("x86_64", "x86", "armeabi-v7a", "arm64-v8a")
        const val NATIVE_LIB_DIR_NAME = "lib"
        const val BASE_APK_FILE_NAME = "base.apk"
        const val ANDROID_MANIFEST_FILE_NAME = "AndroidManifest.xml"

        // ======== path to memory scanner engine lib ==============
        /*
     * For concatenating resource file/folder path
     * we can't use File's constructor to do that because
     * the result will be platform independent
     *
     * example: in windows, using File constructor
     *  		and getting the absoulte path string
     *			will return full path starting with 'C:'

     * which is not what expected of
     * `getClass().getResourceAsStream` in `Resource.CopyResourceFile`
     * (has to start with '/')
     */
        // native lib
        const val MEM_SCANNER_LIB_NAME = "liblib_ACE.so"
        val MEM_SCANNER_LIB_RESOURCE_DIR = "/" + java.lang.String.join("/", "AceAndroidLib", "code_to_inject", "lib")

        // smali code
        const val MEM_SCANNER_SMALI_DIR_NAME = "AceInjector"

        // code to inject's path from resource
        val MEM_SCANNER_SMALI_BASE_DIR = "/" + java.lang.String.join("/", "AceAndroidLib", "code_to_inject", "smali", "com")
        const val MEM_SCANNER_SMALI_ZIP_NAME = MEM_SCANNER_SMALI_DIR_NAME + ".zip"
        val MEM_SCANNER_SMALI_CODE_ZIP_PATH = java.lang.String.join("/", MEM_SCANNER_SMALI_BASE_DIR, MEM_SCANNER_SMALI_ZIP_NAME)

        // smali code to start the service
        const val MEM_SCANNER_CONSTRUCTOR_SMALI_CODE = "invoke-static {}, Lcom/AceInjector/utils/Injector;->Init()V"

        fun LaunchableActivityToSmaliRelativePath(launchableActivity: String): String {

            // replace the '.' in launchableActivity class
            // to a near complete path
            var relativePath = launchableActivity.replace(".", File.separator)
            // don't forget the file extension
            relativePath += ".smali"
            return relativePath
        }


        fun MemScannerFindInjectionLineNum(launchableSmaliFile: String): Int {
            val entrySmaliPath = File(launchableSmaliFile).toPath()
            val fileData = Files.readAllLines(entrySmaliPath, Charset.defaultCharset())
            for (i in fileData.indices) {
                val code = fileData[i]
                if (code.endsWith("constructor <init>()V")) return i
            }
            return -1
        }


        fun AddMemScannerConstructorSmaliCode(launchableSmaliFile: String): List<String> {
            val entrySmaliPath = File(launchableSmaliFile).toPath()
            val fileData = Files.readAllLines(entrySmaliPath, Charset.defaultCharset())
            val injectionLine = MemScannerFindInjectionLineNum(launchableSmaliFile)
            fileData.add(injectionLine + 1, MEM_SCANNER_CONSTRUCTOR_SMALI_CODE)
            logger.info { "Injecting code at: ${launchableSmaliFile}:${injectionLine + 1} " }
            return fileData
        }
    }
}
