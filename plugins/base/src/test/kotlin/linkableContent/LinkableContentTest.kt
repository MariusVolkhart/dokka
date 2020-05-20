package linkableContent

import org.jetbrains.dokka.SourceLinkDefinitionImpl
import org.jetbrains.dokka.base.DokkaBase
import org.jetbrains.dokka.base.transformers.pages.samples.DefaultSamplesTransformer
import org.jetbrains.dokka.base.transformers.pages.sourcelinks.SourceLinksTransformer
import org.jetbrains.dokka.base.translators.documentables.PageContentBuilder
import org.jetbrains.dokka.pages.*
import org.jetbrains.dokka.plugability.plugin
import org.jetbrains.dokka.testApi.testRunner.AbstractCoreTest
import org.jetbrains.kotlin.utils.addToStdlib.cast
import org.jetbrains.kotlin.utils.addToStdlib.safeAs
import org.junit.jupiter.api.Assertions
import org.junit.jupiter.api.Disabled
import org.junit.jupiter.api.Test
import java.nio.file.Paths

class LinkableContentTest : AbstractCoreTest() {

    @Test
    fun `Include module and package documentation`() {

        val testDataDir = getTestDataDir("multiplatform/basicMultiplatformTest").toAbsolutePath()
        val includesDir = getTestDataDir("linkable/includes").toAbsolutePath()

        val configuration = dokkaConfiguration {
            passes {
                pass {
                    moduleName = "example"
                    analysisPlatform = "js"
                    sourceRoots = listOf("jsMain", "commonMain", "jvmAndJsSecondCommonMain").map {
                        Paths.get("$testDataDir/$it/kotlin").toString()
                    }
                    sourceSetName = "js"
                    includes = listOf(Paths.get("$includesDir/include2.md").toString())
                }
                pass {
                    moduleName = "example"
                    analysisPlatform = "jvm"
                    sourceRoots = listOf("jvmMain", "commonMain", "jvmAndJsSecondCommonMain").map {
                        Paths.get("$testDataDir/$it/kotlin").toString()
                    }
                    sourceSetName = "jvm"
                    includes = listOf(Paths.get("$includesDir/include1.md").toString())
                }
            }
        }

        testFromData(configuration) {
            documentablesMergingStage = {
                Assertions.assertEquals(2, it.documentation.size)
                Assertions.assertEquals(2, it.packages.size)
                Assertions.assertEquals(1, it.packages.first().documentation.size)
                Assertions.assertEquals(1, it.packages.last().documentation.size)
            }
        }

    }

    @Test
    fun `Sources multiplatform class documentation`() {

        val testDataDir = getTestDataDir("linkable/sources").toAbsolutePath()

        val configuration = dokkaConfiguration {
            passes {
                pass {
                    moduleName = "example"
                    analysisPlatform = "js"
                    sourceRoots = listOf("$testDataDir/jsMain/kotlin")
                    sourceLinks = listOf(
                        SourceLinkDefinitionImpl(
                            path = "jsMain/kotlin",
                            url = "https://github.com/user/repo/tree/master/src/jsMain/kotlin",
                            lineSuffix = "#L"
                        )
                    )
                    sourceSetName = "js"
                }
                pass {
                    moduleName = "example"
                    analysisPlatform = "jvm"
                    sourceRoots = listOf("$testDataDir/jvmMain/kotlin")
                    sourceLinks = listOf(
                        SourceLinkDefinitionImpl(
                            path = "jvmMain/kotlin",
                            url = "https://github.com/user/repo/tree/master/src/jvmMain/kotlin",
                            lineSuffix = "#L"
                        )
                    )
                    sourceSetName = "jvm"
                }
            }
        }

        testFromData(configuration) {
            renderingStage = { rootPageNode, dokkaContext ->
                val newRoot = SourceLinksTransformer(dokkaContext,
                    PageContentBuilder(
                        dokkaContext.single(dokkaContext.plugin<DokkaBase>().commentsToContentConverter),
                        dokkaContext.single(dokkaContext.plugin<DokkaBase>().signatureProvider),
                        dokkaContext.logger
                    )
                ).invoke(rootPageNode)
                val moduleChildren = newRoot.children
                Assertions.assertEquals(1, moduleChildren.size)
                val packageChildren = moduleChildren.first().children
                Assertions.assertEquals(2, packageChildren.size)
                packageChildren.forEach {
                    val name = it.name.substringBefore("Class")
                    val crl = it.safeAs<ClasslikePageNode>()?.content?.safeAs<ContentGroup>()?.children?.last()
                        ?.safeAs<ContentGroup>()?.children?.last()?.safeAs<ContentGroup>()?.children?.lastOrNull()
                        ?.safeAs<ContentTable>()?.children?.singleOrNull()?.safeAs<ContentGroup>()?.children?.singleOrNull().safeAs<ContentResolvedLink>()
                    Assertions.assertEquals(
                        "https://github.com/user/repo/tree/master/src/${name.toLowerCase()}Main/kotlin/${name}Class.kt#L3",
                        crl?.address
                    )
                }
            }
        }
    }

    @Test
    fun `Samples multiplatform documentation`() {

        val testDataDir = getTestDataDir("linkable/samples").toAbsolutePath()

        val configuration = dokkaConfiguration {
            passes {
                pass {
                    moduleName = "example"
                    analysisPlatform = "js"
                    sourceRoots = listOf("$testDataDir/jsMain/kotlin")
                    sourceSetName = "js"
                    samples = listOf("$testDataDir/jsMain/resources/Samples.kt")
                }
                pass {
                    moduleName = "example"
                    analysisPlatform = "jvm"
                    sourceRoots = listOf("$testDataDir/jvmMain/kotlin")
                    sourceSetName = "jvm"
                    samples = listOf("$testDataDir/jvmMain/resources/Samples.kt")
                }
            }
        }

        testFromData(configuration) {
            renderingStage = { rootPageNode, dokkaContext ->
                val newRoot = DefaultSamplesTransformer(dokkaContext).invoke(rootPageNode)

                val moduleChildren = newRoot.children
                Assertions.assertEquals(1, moduleChildren.size)
                val packageChildren = moduleChildren.first().children
                Assertions.assertEquals(2, packageChildren.size)
                packageChildren.forEach {
                    val name = it.name.substringBefore("Class")
                    val classChildren = it.children
                    Assertions.assertEquals(2, classChildren.size)
                    val function = classChildren.find { it.name == "printWithExclamation" }
                    val text = function.cast<MemberPageNode>().content.cast<ContentGroup>().children.last()
                        .cast<ContentDivergentGroup>().children.single()
                        .cast<ContentDivergentInstance>().after
                        .cast<ContentGroup>().children.last()
                        .cast<ContentGroup>().children.last()
                        .cast<PlatformHintedContent>().children.single()
                        .cast<ContentGroup>().children.single()
                        .cast<ContentTable>().children.single()
                        .cast<ContentGroup>().children.single()
                        .cast<ContentGroup>().children.single()
                        .cast<ContentCode>().children.single().cast<ContentText>().text
                    Assertions.assertEquals(
                        "${name}Class().printWithExclamation(\"Hi, $name\")",
                        text
                    )
                }
            }
        }
    }
}