package org.jetbrains.dokka

import org.jetbrains.dokka.DokkaConfiguration.ExternalDocumentationLink
import org.jetbrains.dokka.utilities.DokkaConsoleLogger
import java.io.File
import java.io.FileNotFoundException
import java.net.MalformedURLException
import java.net.URL
import java.net.URLClassLoader
import java.nio.file.Files
import java.nio.file.Paths

open class GlobalArguments(parser: DokkaArgumentsParser) : DokkaConfiguration {
    override val outputDir: String by parser.stringOption(
        listOf("-output"),
        "Output directory path",
        ""
    )

    override val format: String by parser.stringOption(
        listOf("-format"),
        "Output format (text, html, gfm, jekyll, kotlin-website)",
        ""
    )

    override val pluginsClasspath: List<File> by parser.repeatableOption(
        listOf("-dokkaPlugins"),
        "List of jars with dokka plugins"
    ) {
        File(it)
    }.also {
        Paths.get("./dokka-base.jar").toAbsolutePath().normalize().run {
            if (Files.exists(this)) it.value.add(this.toFile())
            else throw FileNotFoundException("Dokka base plugin is not found! Make sure you placed 'dokka-base.jar' containing base plugin along the cli jar file")
        }
    }

    override val cacheRoot: String? by parser.stringOption(
        listOf("-cacheRoot"),
        "Path to cache folder, or 'default' to use ~/.cache/dokka, if not provided caching is disabled",
        null
    )

    override val offlineMode: Boolean by parser.singleFlag(
        listOf("-offlineMode"),
        "Offline mode (do not download package lists from the Internet)"
    )

    override val passesConfigurations: List<Arguments> by parser.repeatableFlag(
        listOf("-pass"),
        "Single dokka pass"
    ) {
        Arguments(parser).also { if (it.moduleName.isEmpty()) DokkaConsoleLogger.warn("Not specified module name. It can result in unexpected behaviour while including documentation for module") }
    }

    override val pluginsConfiguration: Map<String, String> = mutableMapOf()
}

class Arguments(val parser: DokkaArgumentsParser) : DokkaConfiguration.PassConfiguration {
    override val moduleName: String by parser.stringOption(
        listOf("-module"),
        "Name of the documentation module",
        ""
    )

    override val displayName: String by parser.stringOption(
        listOf("-displayName"),
        "Name displayed in the generated documentation",
        ""
    )

    override val sourceSetID: String by parser.stringOption(
        listOf("-sourceSetID"),
        "Source set ID used for declaring dependent source sets",
        "main"
    )

    override val classpath: List<String> by parser.repeatableOption<String>(
        listOf("-classpath"),
        "Classpath for symbol resolution"
    )

    override val sourceRoots: List<DokkaConfiguration.SourceRoot> by parser.repeatableOption(
        listOf("-src"),
        "Source file or directory (allows many paths separated by the system path separator)"
    ) { SourceRootImpl(it) }

    override val dependentSourceSets: List<String> by parser.repeatableOption<String>(
        listOf("-dependentSets"),
        "Names of dependent source sets"
    )

    override val samples: List<String> by parser.repeatableOption<String>(
        listOf("-sample"),
        "Source root for samples"
    )

    override val includes: List<String> by parser.repeatableOption<String>(
        listOf("-include"),
        "Markdown files to load (allows many paths separated by the system path separator)"
    )

    override val includeNonPublic: Boolean by parser.singleFlag(
        listOf("-includeNonPublic"),
        "Include non public"
    )

    override val includeRootPackage: Boolean by parser.singleFlag(
        listOf("-includeRootPackage"),
        "Include root package"
    )

    override val reportUndocumented: Boolean by parser.singleFlag(
        listOf("-reportUndocumented"),
        "Report undocumented members"
    )

    override val skipEmptyPackages: Boolean by parser.singleFlag(
        listOf("-skipEmptyPackages"),
        "Do not create index pages for empty packages"
    )

    override val skipDeprecated: Boolean by parser.singleFlag(
        listOf("-skipDeprecated"),
        "Do not output deprecated members"
    )

    override val jdkVersion: Int by parser.singleOption(
        listOf("-jdkVersion"),
        "Version of JDK to use for linking to JDK JavaDoc",
        { it.toInt() },
        { 8 }
    )

    override val languageVersion: String? by parser.stringOption(
        listOf("-languageVersion"),
        "Language Version to pass to Kotlin analysis",
        null
    )

    override val apiVersion: String? by parser.stringOption(
        listOf("-apiVersion"),
        "Kotlin Api Version to pass to Kotlin analysis",
        null
    )

    override val noStdlibLink: Boolean by parser.singleFlag(
        listOf("-noStdlibLink"),
        "Disable documentation link to stdlib"
    )

    override val noJdkLink: Boolean by parser.singleFlag(
        listOf("-noJdkLink"),
        "Disable documentation link to JDK"
    )

    override val suppressedFiles: List<String> by parser.repeatableOption<String>(
        listOf("-suppressedFile"),
        ""
    )


    override val analysisPlatform: Platform by parser.singleOption(
        listOf("-analysisPlatform"),
        "Platform for analysis",
        { Platform.fromString(it) },
        { Platform.DEFAULT }
    )


    override val perPackageOptions: MutableList<DokkaConfiguration.PackageOptions> by parser.singleOption(
        listOf("-packageOptions"),
        "List of package passConfiguration in format \"prefix,-deprecated,-privateApi,+warnUndocumented,+suppress;...\" ",
        { parsePerPackageOptions(it).toMutableList() },
        { mutableListOf() }
    )

    override val externalDocumentationLinks: MutableList<DokkaConfiguration.ExternalDocumentationLink> by parser.singleOption(
        listOf("-links"),
        "External documentation links in format url^packageListUrl^^url2...",
        { MainKt.parseLinks(it).toMutableList() },
        { mutableListOf() }
    )

    override val sourceLinks: MutableList<DokkaConfiguration.SourceLinkDefinition> by parser.repeatableOption<DokkaConfiguration.SourceLinkDefinition>(
        listOf("-srcLink"),
        "Mapping between a source directory and a Web site for browsing the code"
    ) {
        if (it.isNotEmpty() && it.contains("="))
            SourceLinkDefinitionImpl.parseSourceLinkDefinition(it)
        else {
            throw IllegalArgumentException("Warning: Invalid -srcLink syntax. Expected: <path>=<url>[#lineSuffix]. No source links will be generated.")
        }
    }
}

object MainKt {
    fun defaultLinks(config: DokkaConfiguration.PassConfiguration): MutableList<ExternalDocumentationLink> =
        mutableListOf<ExternalDocumentationLink>().apply {
            if (!config.noJdkLink)
                this += DokkaConfiguration.ExternalDocumentationLink
                    .Builder("https://docs.oracle.com/javase/${config.jdkVersion}/docs/api/")
                    .build()

            if (!config.noStdlibLink)
                this += DokkaConfiguration.ExternalDocumentationLink
                    .Builder("https://kotlinlang.org/api/latest/jvm/stdlib/")
                    .build()
        }

    fun parseLinks(links: String): List<ExternalDocumentationLink> {
        val (parsedLinks, parsedOfflineLinks) = links.split("^^")
            .map { it.split("^").map { it.trim() }.filter { it.isNotBlank() } }
            .filter { it.isNotEmpty() }
            .partition { it.size == 1 }

        return parsedLinks.map { (root) -> ExternalDocumentationLink.Builder(root).build() } +
                parsedOfflineLinks.map { (root, packageList) ->
                    val rootUrl = URL(root)
                    val packageListUrl =
                        try {
                            URL(packageList)
                        } catch (ex: MalformedURLException) {
                            File(packageList).toURI().toURL()
                        }
                    ExternalDocumentationLink.Builder(rootUrl, packageListUrl).build()
                }
    }

    @JvmStatic
    fun entry(configuration: DokkaConfiguration) {
        val generator = DokkaGenerator(configuration, DokkaConsoleLogger)
        generator.generate()
        DokkaConsoleLogger.report()
    }

    fun findToolsJar(): File {
        val javaHome = System.getProperty("java.home")
        val default = File(javaHome, "../lib/tools.jar")
        val mac = File(javaHome, "../Classes/classes.jar")
        return when {
            default.exists() -> default
            mac.exists() -> mac
            else -> {
                throw Exception("tools.jar not found, please check it, also you can provide it manually, using -cp")
            }
        }
    }

    fun createClassLoaderWithTools(): ClassLoader {
        val toolsJar = findToolsJar().canonicalFile.toURI().toURL()
        val originalUrls = (javaClass.classLoader as? URLClassLoader)?.urLs
        val dokkaJar = javaClass.protectionDomain.codeSource.location
        val urls = if (originalUrls != null) arrayOf(toolsJar, *originalUrls) else arrayOf(toolsJar, dokkaJar)
        return URLClassLoader(urls, ClassLoader.getSystemClassLoader().parent)
    }

    fun startWithToolsJar(configuration: DokkaConfiguration) {
        try {
            javaClass.classLoader.loadClass("com.sun.tools.doclets.formats.html.HtmlDoclet")
            entry(configuration)
        } catch (e: ClassNotFoundException) {
            val classLoader = createClassLoaderWithTools()
            classLoader.loadClass("org.jetbrains.dokka.MainKt")
                .methods.find { it.name == "entry" }!!
                .invoke(null, configuration)
        }
    }

    fun createConfiguration(args: Array<String>): GlobalArguments {
        val parseContext = ParseContext()
        val parser = DokkaArgumentsParser(args, parseContext)
        val configuration = GlobalArguments(parser)

        parseContext.cli.singleAction(
            listOf("-globalPackageOptions"),
            "List of package passConfiguration in format \"prefix,-deprecated,-privateApi,+warnUndocumented,+suppress;...\" "
        ) { link ->
            configuration.passesConfigurations.all {
                it.perPackageOptions.toMutableList().addAll(parsePerPackageOptions(link))
            }
        }

        parseContext.cli.singleAction(
            listOf("-globalLinks"),
            "External documentation links in format url^packageListUrl^^url2..."
        ) { link ->
            configuration.passesConfigurations.all {
                it.externalDocumentationLinks.toMutableList().addAll(parseLinks(link))
            }
        }

        parseContext.cli.repeatingAction(
            listOf("-globalSrcLink"),
            "Mapping between a source directory and a Web site for browsing the code"
        ) {
            val newSourceLinks = if (it.isNotEmpty() && it.contains("="))
                listOf(SourceLinkDefinitionImpl.parseSourceLinkDefinition(it))
            else {
                if (it.isNotEmpty()) {
                    DokkaConsoleLogger.warn("Invalid -srcLink syntax. Expected: <path>=<url>[#lineSuffix]. No source links will be generated.")
                }
                listOf()
            }

            configuration.passesConfigurations.all {
                it.sourceLinks.toMutableList().addAll(newSourceLinks)
            }
        }
        parser.parseInto(configuration)
        configuration.passesConfigurations.forEach {
            it.externalDocumentationLinks.addAll(defaultLinks(it))
        }
        return configuration
    }

    @JvmStatic
    fun main(args: Array<String>) {
        val configuration = createConfiguration(args)

        if (configuration.format.toLowerCase() == "javadoc")
            startWithToolsJar(configuration)
        else
            entry(configuration)
    }
}



