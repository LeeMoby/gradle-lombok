package io.franzbecker.gradle.lombok

/**
 * Integration tests for {@link LombokPlugin}.
 */
class LombokPluginIntegrationTest extends AbstractIntegrationTest {

    private static final LOMBOK_VERSION = "1.16.4"

    def setup() {
        buildFile << """
            lombok {
                version = "${LOMBOK_VERSION}"
                sha256 = "3ca225ce3917eac8bf4b7d2186845df4e70dcdede356dca8537b6d78a535c91e"
            }
        """.stripIndent()
    }
    
    /**
     * Creates a Java class with a method annotated with @SneakyThrows and tests if the
     * annotation is applied properly.
     */
    def "Can compile and test code with @SneakyThrows annotation."() {
        given: "a valid build configuration"
        buildFile << """
            dependencies {
                testCompile 'junit:junit:4.12'
            }
        """.stripIndent()

        and: "source code with @SneakyThrows to compile and test"
        createSneakyThrowsJavaSource()
        createSneakyThrowsTestCode()

        when: "calling gradle test"
        runBuild('test')

        then: "build is successful and both class file exist"
        noExceptionThrown()
        new File(projectDir, "build/classes/java/main/com/example/SneakyHelloWorld.class").exists()
        new File(projectDir, "build/classes/java/test/com/example/SneakyHelloWorldTest.class").exists()
    }

    def "Works with the java-library plugin"() {
        given:
        buildFile.text = buildFile.text.replace("id 'java'", "id 'java-library'")
        createSimpleTestCase()

        when:
        runBuild('test')

        then:
        noExceptionThrown()
        new File(projectDir, "build/classes/java/main/com/example/HelloWorld.class").exists()
        new File(projectDir, "build/classes/java/test/com/example/HelloWorldTest.class").exists()
    }

    /**
     * Verifies that the Lombok dependency does not occur in the generated POM.
     */
    def "Dependency does not occur in generated POM"() {
        given: "a valid build configuration"
        buildFile << """
            apply plugin: 'maven'

            dependencies {
                testCompile 'junit:junit:4.12'
            }
        """.stripIndent()

        when: "calling gradle install"
        runBuild('install')

        then: "Verify that the POM exists"
        def pom = new File(projectDir, "build/poms/pom-default.xml")
        assert pom.exists()

        and: "Parse POM and retrieve <dependencies> object"
        def pomXml = new XmlSlurper().parseText(pom.text)
        def dependencies = pomXml.dependencies
        assert dependencies.size() == 1

        and: "Verify that JUnit dependency exists"
        def junit = dependencies.dependency.find { it.artifactId.text() == 'junit' }
        assert junit

        and: "Verify that Lombok dependency does not exist"
        def lombok = dependencies.dependency.find { it.artifactId.text() == 'lombok' }
        assert !lombok
    }

    /**
     * Verifies that the Lombok dependency is on the Eclipse classpath.
     */
    def "Lombok dependency is on Eclipse classpath"() {
        given:
        buildFile << """
            apply plugin: 'eclipse'
        """.stripIndent()

        when:
        runBuild('eclipse')

        then: "verify that the .classpath file exists"
        def dotClasspath = new File(projectDir, ".classpath")
        dotClasspath.exists()

        and: "verify the lombok JAR is included"
        def classpath = new XmlSlurper().parse(dotClasspath)
        def lombokEntry = classpath.classpathentry.find { node ->
            node.@kind == "lib" && node.@path.text().contains("lombok-${LOMBOK_VERSION}.jar")
        }
        assert lombokEntry
    }

    /**
     * Verifies that the Lombok dependency is on the IntelliJ classpath.
     */
    def "Lombok dependency is on IntelliJ classpath"() {
        given:
        buildFile << """
            apply plugin: 'idea'
        """.stripIndent()

        when:
        runBuild('idea')

        then: "verify that the .iml file exists"
        File imlFile = getImlFile()

        and: "verify the lombok JAR is included"
        def module = new XmlSlurper().parse(imlFile)
        def provided = module.component.orderEntry.find { it.@type == "module-library" }
        assert provided
        def lombokEntry = provided.library.CLASSES.root.find { it.@url.text().contains("lombok-${LOMBOK_VERSION}.jar") }
        assert lombokEntry
    }

    private File getImlFile() {
        FilenameFilter filter = { dir, name -> name.endsWith(".iml") }
        File[] imlFiles = projectDir.listFiles(filter)
        assert imlFiles.size() == 1
        return imlFiles[0]
    }

    private void createSneakyThrowsJavaSource() {
        def file = createFile("src/main/java/com/example/SneakyHelloWorld.java")
        file << """
            package com.example;

            import lombok.SneakyThrows;
            import java.io.UnsupportedEncodingException; // checked exception

            public class SneakyHelloWorld {

                @SneakyThrows(UnsupportedEncodingException.class)
                public byte[] throwingStuff() {
                    return "test".getBytes("unsupported");
                }

            }
        """.stripIndent()
    }

    private void createSneakyThrowsTestCode() {
        def file = createFile("src/test/java/com/example/SneakyHelloWorldTest.java")
        file << """
            package com.example;

            import org.junit.Assert;
            import org.junit.Test;
            import java.io.UnsupportedEncodingException;

            public class SneakyHelloWorldTest {

                @Test(expected = UnsupportedEncodingException.class)
                public void testThrowingStuff() {
                    SneakyHelloWorld instance = new SneakyHelloWorld();
                    instance.throwingStuff();
                }

            }

        """.stripIndent()
    }

}
