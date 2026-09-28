# Arch Unit

A written rule can be forgotten. ArchUnit is a testing library that turns the rule into an automated test that fails the build if someone breaks it:

```java
@AnalyzeClasses(packages = "com.yourorg.rag")
class LayeringTest {

    @ArchTest
    static final ArchRule controllers_do_not_access_repositories =
        noClasses().that().resideInAPackage("..controller..")
            .should().dependOnClassesThat().resideInAPackage("..repository..");

    @ArchTest
    static final ArchRule logic_does_not_depend_on_repositories =
        noClasses().that().resideInAPackage("..logic..")
            .should().dependOnClassesThat().resideInAPackage("..repository..");
}
```

if you accidentally inject a repository into a controller, this test goes red immediately. It's optional because it adds a dependency anda  little setup, but for a solo project it's a cheap way to keep yourself honest. On a team it is more valuable still.

## Sumary of what 'done' looks like
1. All packages exist and are committed (with package-info.java or .gitkeep)
2. CONTRRIBUTING.md states the layering rule.
3. (Optional) A LayeringTest runs with your normal tests and pases.
