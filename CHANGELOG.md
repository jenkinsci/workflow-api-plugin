## Changelog

### 2.41

Release date: 2021-01-19

* Fix: Stop `FlowNode.addOrReplaceAction` from incorrectly throwing an `UnsupportedOperationException` when replacing an existing action ([JENKINS-64438](https://issues.jenkins.io/browse/JENKINS-64438))
* Improvement: Allow users with Overall/Manage permission to configure the Pipeline Default Speed/Durability Level section on the global configuration page ([JENKINS-62303](https://issues.jenkins.io/browse/JENKINS-62303))
* Internal: Update minimum Jenkins version to 2.176.4, update dependencies and parent POM, and start using the plugin BOM ([PR #121](https://github.com/jenkinsci/workflow-api-plugin/pull/121), [PR #122](https://github.com/jenkinsci/workflow-api-plugin/pull/122), [PR #123](https://github.com/jenkinsci/workflow-api-plugin/pull/123), [PR #126](https://github.com/jenkinsci/workflow-api-plugin/pull/126), [PR #127](https://github.com/jenkinsci/workflow-api-plugin/pull/127), [PR #129](https://github.com/jenkinsci/workflow-api-plugin/pull/129), [PR #130](https://github.com/jenkinsci/workflow-api-plugin/pull/130), [PR #131](https://github.com/jenkinsci/workflow-api-plugin/pull/131), [PR #132](https://github.com/jenkinsci/workflow-api-plugin/pull/132))

### 2.40

Release date: 2020-02-26

* This plugin now requires Jenkins 2.150.3 or newer.
* Internal: Update test dependencies and tests to fix PCT issues. ([PR 119](https://github.com/jenkinsci/workflow-api-plugin/pull/119))

### 2.39

Release date: 2020-01-30

* Internal: Remove references to Trilead to fix PCT issues when using newer versions of Jenkins. ([PR 116](https://github.com/jenkinsci/workflow-api-plugin/pull/116))
* Internal: Update parent POM and project URL. ([PR 115](https://github.com/jenkinsci/workflow-api-plugin/pull/115))
* Internal: Fix typos in CHANGELOG.md. ([PR 114](https://github.com/jenkinsci/workflow-api-plugin/pull/114))

### 2.38

Release date: 2019-12-06

-   Fix: Proxy `MissingPropertyException` when the receiver's class is
    implemented in a Pipeline script to avoid leaking the class loader for
    the Pipeline script through `ErrorAction`. ([PR
    110](https://github.com/jenkinsci/workflow-api-plugin/pull/110))
-   Improvement: Show the stack trace for failed Pipeline steps in the Pipeline Steps view. ([PR 103](https://github.com/jenkinsci/workflow-api-plugin/pull/103))
-   Internal: Refactor various parts of the codebase, improve tests, and migrate documentation from wiki to GitHub. ([PR 104](https://github.com/jenkinsci/workflow-api-plugin/pull/104), [PR 105](https://github.com/jenkinsci/workflow-api-plugin/pull/105), [PR 107](https://github.com/jenkinsci/workflow-api-plugin/pull/107), [PR 108](https://github.com/jenkinsci/workflow-api-plugin/pull/108), [PR 109](https://github.com/jenkinsci/workflow-api-plugin/pull/109), [PR 112](https://github.com/jenkinsci/workflow-api-plugin/pull/112))

### 2.37

Release date: 2019-08-29

-   Fix: Proxy exceptions when the exception class is implemented in a
    Pipeline script to avoid leaking the class loader for the Pipeline
    script through `ErrorAction`. ([PR
    102](https://github.com/jenkinsci/workflow-api-plugin/pull/102))
-   Fix: Avoid leaking `ThreadLocal` variables used in buffering-related
    logic for Pipeline logs.
    ([JENKINS-58899](https://issues.jenkins-ci.org/browse/JENKINS-58899))
-   Internal: Update tests to fix PCT failures. ([PR
    99](https://github.com/jenkinsci/workflow-api-plugin/pull/99))

### 2.36

Release date: 2019-08-01

-   Developer: `TaskListenerDecorator` API is now stable instead of a
    beta API. ([PR
    97](https://github.com/jenkinsci/workflow-api-plugin/pull/97))
-   Developer: Introduce new `StepListener` API to allow interception of
    step execution. ([PR
    96](https://github.com/jenkinsci/workflow-api-plugin/pull/96))
-   Developer: Introduce new `FlowExecutionListener.onCreated` method.
    ([PR
    92](https://github.com/jenkinsci/workflow-api-plugin/pull/92))

### 2.35

Release date: 2019-06-07

-   Fix: Prevent
    `StandardGraphLookupView.bruteForceScanForEnclosingBlocks` from
    throwing `IndexOutOfBoundsException` in some scenarios.
    ([JENKINS-57805](https://issues.jenkins-ci.org/browse/JENKINS-57805))
-   Fix: Catch additional types of exceptions when iterating
    through `FlowExecutionList`. Fixes some cases where Jenkins might
    fail to start because of a problem with a single build. ([PR
    93](https://github.com/jenkinsci/workflow-api-plugin/pull/93))

### 2.34

Release date: 2019-05-10

-   Improvement: Add the name of the stash to the exception thrown when
    trying to create an empty stash. ([PR
    86](https://github.com/jenkinsci/workflow-api-plugin/pull/86))
-   Fix: Use the correct parameter to set `nodeAfter` in the
    4-parameter `MemoryFlowChunk` constructor. ([PR
    89](https://github.com/jenkinsci/workflow-api-plugin/pull/89))
-   Developer: Add a new API called `WarningAction` that can be added to
    a `FlowNode` to indicate that some non-fatal event occurred during
    execution of a step even though the step completed normally.
    ([JENKINS-43995](https://issues.jenkins-ci.org/browse/JENKINS-43995), [JENKINS-39203](https://issues.jenkins-ci.org/browse/JENKINS-39203))

### 2.33

Release date: 2018-11-19

-   [JENKINS-54566](https://issues.jenkins-ci.org/browse/JENKINS-54566):
    Prevent the error "Failed to execute command Pipe.Flush(-1)" from
    occurring by flushing streams before they have been garbage
    collected.

### 2.32

Release date: 2018-11-09

-   Developer: Add an SPI for `LogStorage` implementations to
    satisfy `WorkflowRun#getLogFile`. (Part
    of [JENKINS-54128](https://issues.jenkins-ci.org/browse/JENKINS-54128),
    but version 2.29 of Pipeline Job Plugin contains the actual fix)

### 2.31

Release date: 2018-10-26

-   [JENKINS-54073](https://issues.jenkins-ci.org/browse/JENKINS-54073):
    Buffer remote log output to fix logging-related performance issues.

### 2.30

Release date: 2018-10-12

-   [JEP-210](https://jenkins.io/jep/210): redesigned
    log storage system for Pipeline builds. Should have no effect
    unless [Pipeline Job
    Plugin](https://plugins.jenkins.io/workflow-job) is
    also updated.
-   [JENKINS-45693](https://issues.jenkins-ci.org/browse/JENKINS-45693): `TaskListenerDecorator` API.

-   Improvement: Mark interrupted steps using a gray ball instead of a
    red ball in the Pipeline steps view to distinguish them from
    failures.

### 2.30-beta-1

Release date: 2018-10-04

-   [JEP-210](https://jenkins.io/jep/210): redesigned
    log storage system for Pipeline builds. Should have no effect
    unless [Pipeline Job
    Plugin](https://plugins.jenkins.io/workflow-job)
    is also updated.
-   [JENKINS-45693](https://issues.jenkins-ci.org/browse/JENKINS-45693): `TaskListenerDecorator`
    API.

-   Improvement: Mark interrupted steps using a gray ball instead of a
    red ball in the Pipeline steps view to distinguish them from
    failures.

### 2.29

Release date: 2018-07-24

-   No user-visible changes - test utilities for ArtifactManager

### 2.28

Release date: 2018-06-15

-   **Now requires Jenkins core 2.121**
-   Support for Incremental releases
-   **Beta**: Support for VirtualFile use with stash & artifacts
    ([JENKINS-49635](https://issues.jenkins-ci.org/browse/JENKINS-49635))
-   Minor fix to displayed message format

### 2.27

Release date: 2018-04-12

-   Add ability to insert a placeholder for Step Arguments that cannot
    be serialized (API to
    support [JENKINS-50752](https://issues.jenkins-ci.org/browse/JENKINS-50752)
    fix)
-   Improvement/Bugfix: Catch all errors thrown when saving the FlowNode
    in an error-safe way, to allow processes to complete normally 

### 2.26

Release date: 2018-02-23

-   Bugfix: Deal with additional unserializable Throwable types
    ([JENKINS-49025](https://issues.jenkins-ci.org/browse/JENKINS-49025))

### 2.25

Release date: 2018-01-22

-   **Now Requires Java 8** (core 2.60.3+)
-   Major new feature: Durability Settings & Ability To Disable Pipeline
    Resume  
    -   Object and APIs to pass Durability Settings into an Execution
        - [JENKINS-47300](https://issues.jenkins-ci.org/browse/JENKINS-47300)
    -   API for disabling resume for a
        Pipeline [JENKINS-33761](https://issues.jenkins-ci.org/browse/JENKINS-33761)
    -   UI for setting a global default Durability setting to apply to
        pipelines
-   Small micro-optimization to reduce garbage generated when displaying
    arguments for step

### 2.24

Release date: 2017-12-04

-   [JENKINS-47725](https://issues.jenkins-ci.org/browse/JENKINS-47725) -
    Fix a WeakHashMap synchronization issue
-   Minor POM changes & making an API slightly more restrictive in
    Generics it returns

### 2.23.1

Release date: 2017-10-24

-   Revert [JENKINS-40912](https://issues.jenkins-ci.org/browse/JENKINS-40912) - 
    the change caused stash steps to hang in specific cases.  Will be
    amended and re-released with fixes.

### 2.23

Release date: 2017-10-24

-   [JENKINS-40912](https://issues.jenkins-ci.org/browse/JENKINS-40912) -
    return list of files for stashing and unstashing

### 2.22

Release date: 2017-09-26

-   New APIs to provide fast access to information about the structure
    of the pipeline graph
    -   Provides enclosing block information for nodes
        ([JENKINS-27395](https://issues.jenkins-ci.org/browse/JENKINS-27395)
        and partial implementation of
        [JENKINS-37573](https://issues.jenkins-ci.org/browse/JENKINS-37573))
    -   Lets us run parallels with numerous branches far more quickly by
        adding an isActive API & making it performant
        ([JENKINS-45553](https://issues.jenkins-ci.org/browse/JENKINS-45553)
    -   Provides a more correct isActive API rather than isRunning to
        determine if a step or block is complete or not
        ([JENKINS-38223](https://issues.jenkins-ci.org/browse/JENKINS-38223))

### 2.20

Release date: 2017-08-01

-   Make the PersistentAction API public to help with optimizing
    frequent action lookups in other pipeline plugins

### 2.19

Release date: 2017-07-24

-   [JENKINS-44636](https://issues.jenkins-ci.org/browse/JENKINS-44636)
    New `QueueItemAction` for tracking node block queue status.

### 2.18

Release date: 2017-06-29

-   [JENKINS-31582](https://issues.jenkins-ci.org/browse/JENKINS-31582) Addition
    to `ArgumentsAction`.

### 2.17

Release date: 2017-06-05

-   [JENKINS-](https://issues.jenkins-ci.org/browse/JENKINS-43055)[38536](https://issues.jenkins-ci.org/browse/JENKINS-38536)
    Fix finding the last FlowNode for an in-progress parallel with a
    long-running step

### 2.16

Release date: 2017-05-30

-   [JENKINS-43055](https://issues.jenkins-ci.org/browse/JENKINS-43055) Made `GraphListener`
    into an extension point.

-   [JENKINS-37327](https://issues.jenkins-ci.org/browse/JENKINS-37327) API
    allowing empty stashes.

### 2.15

Release date: 2017-05-22

-   [JENKINS-37324](https://issues.jenkins-ci.org/browse/JENKINS-37324) -
    Retain and display arguments to pipeline steps
-   [JENKINS-43055](https://issues.jenkins-ci.org/browse/JENKINS-43055) -
    Add a FlowExecutionListener extension point

### 2.13

Release date: 2017-04-13

-   [JENKINS-42895](https://issues.jenkins-ci.org/browse/JENKINS-42895) Fix
    sanity checks failing when running a pipeline with a parallel
    containing 0 branches

### 2.12

Release date: 2017-03-06

-   [JENKINS-39839](https://issues.jenkins-ci.org/browse/JENKINS-39839)
    GraphAnalysis visitor fix: missing parallel events in specific
    nested/incomplete parallel cases
-   [JENKINS-39841](https://issues.jenkins-ci.org/browse/JENKINS-39841)
    GraphAnalysis visitor fix: null StartNode for certain parallel
    events
-   [JENKINS-41685](https://issues.jenkins-ci.org/browse/JENKINS-41685)
    GraphAnalysis visitor fix: duplicate events with certain parallel
    structures
-   [JENKINS-38536](https://issues.jenkins-ci.org/browse/JENKINS-38536)
    GraphAnalysis visitor fix: part of fix to timing computation for
    incomplete parallels - apply ordering to ensure the parallelEnd
    event triggers on the last parallel branch with activity.
-   Variety of small new GraphAnalysis features, such as sorting
    Comparators for FlowNodes and a one-step method to get all FlowNodes
-   Harden the GraphAnalysis visitor API guarantees and better
    documentation of the APIs and their assumptions + guarantees

### 2.11

Release date: 2017-02-14

-   [JENKINS-40771](https://issues.jenkins-ci.org/browse/JENKINS-40771)
    Race condition when scheduling \>1 Pipeline build simultaneously
    could cause builds to not be recorded in the list of running builds,
    and thus fail to resume after a restart.
-   [JENKINS-39346](https://issues.jenkins-ci.org/browse/JENKINS-39346)
    Certain kinds of nested exceptions could cause a messy build failure
    (extends fix made in 2.5).
-   `FlowCopier` extensions.

### 2.10

Release date: 2017-02-07

-   Add StepNode so plugins can obtain StepDescriptor information for a
    FlowNode without requiring a workflow-cps plugin dependency

### 2.8

Release date: 2016-12-01

-   Fix:
    [JENKINS-38536](https://issues.jenkins-ci.org/browse/JENKINS-38536)
    Resolve case where SimpleBlockVisitor does not find the correct
    **last** branch out of a set of parallels

### 2.7

Release date: 2016-11-29

-   Fix:
    [JENKINS-38089](https://issues.jenkins-ci.org/browse/JENKINS-38089)
    Fix a case where special parallel pipelines would break the graph
    analysis APIs
-   Feature: Add a new TagsAction class to allow attaching metadata (as
    key-value pairs) to steps as a precursor to
    [JENKINS-39522](https://issues.jenkins-ci.org/browse/JENKINS-39522)

### 2.6

Release date: 2016-11-07

-   [JENKINS-39456](https://issues.jenkins-ci.org/browse/JENKINS-39456)
    Reduce memory usage of execution graph.
-   Clarifying Javadoc of `LinearScanner` about passing multiple heads.

### 2.5

Release date: 2016-10-19

-   [JENKINS-38867](https://issues.jenkins-ci.org/browse/JENKINS-38867)
    Improved performance of basic step graph calculations, for example
    as used by the stage view.
-   [JENKINS-34488](https://issues.jenkins-ci.org/browse/JENKINS-34488)
    Various errors when trying to run `assert` statements, and under
    certain other conditions as well.
-   [JENKINS-38640](https://issues.jenkins-ci.org/browse/JENKINS-38640)
    Improved performance of `stash` on large artifacts.

### 2.4

Release date: 2016-09-23

-   [JENKINS-38458](https://issues.jenkins-ci.org/browse/JENKINS-38458) -
    Make DepthFirstScanner obey the same ordering rules as
    FlowGraphWalker, strictly
-   [JENKINS-38309](https://issues.jenkins-ci.org/browse/JENKINS-38309) -
    Make ForkScanner return parallel branches in last-\>first order for
    consistency with its general iteration

### 2.3

Release date: 2016-09-07

-   [JENKINS-31155](https://issues.jenkins-ci.org/browse/JENKINS-31155)
    infrastructure.

### 2.2

Release date: 2016-08-25

-   Major Feature: new suite of APIs for analyzing the graph of
    FlowNodes from an execution  ([see package
    contents](https://github.com/jenkinsci/workflow-api-plugin/tree/master/src/main/java/org/jenkinsci/plugins/workflow/graphanalysis))
    -   New FlowScanner classes, that provide utilities to iterate
        through, visit, or search FlowNodes in a specific order
        -   See
            [JavaDocs](https://github.com/jenkinsci/workflow-api-plugin/blob/master/src/main/java/org/jenkinsci/plugins/workflow/graphanalysis/AbstractFlowScanner.java)
            for more info
        -   LinearScanner (walks through nodes, ignoring all but 1
            branch of a parallel: very fast)
        -   LinearBlockHoppingScanner - like LinearScanner but jumps
            over blocks (only sees preceding or inclusing nodes)
        -   DepthFirstScanner - visits all nodes, walking to start
            before visiting side branches (fairly fast).
        -   ForkScanner - visits all nodes, providing a linear order of
            block iteration (all branches in a parallel are visited
            before continuing)
            -   Provides the more complex APIs below.
    -   SAX-like API to slice the graph into blocks or runs of nodes
        ("Chunks") and return information about them.
        -   ForkScanner.visitSimpleChunks does this execution
        -   SimpleChunkVisitor interface: defines the basic callbacks
            used, implement this when collecting information.
        -   ChunkFinder: defines the boundary conditions for starting &
            ending a chunk
            -   A couple implementations included.
        -   Container classes/APIs for collections of FlowNodes (Classes
            named \*FlowChunk)
            -   Classes \*MemoryFlowChunk offer concrete representations
                that store FlowNodes in memory
        -   StandardChunkVisitor: minimum ChunkVisitor implementation
            (assumes chunks come one after another, no nesting)
            -   Extend me to store lists of chunks. 
-   Fix a NullPointerException with the FlowExecutionList
-   Dev-only: Javadocs & .gitignore changes

### 2.1

Release date: 2016-06-16

-   API for
    [JENKINS-26130](https://issues.jenkins-ci.org/browse/JENKINS-26130).
-   Diagnostics related to
    [JENKINS-34281](https://issues.jenkins-ci.org/browse/JENKINS-34281).

### 2.0

Release date: 2016-04-05

-   First release under per-plugin versioning scheme. See [1.x
    changelog](https://github.com/jenkinsci/workflow-plugin/blob/82e7defa37c05c5f004f1ba01c93df61ea7868a5/CHANGES.md)
    for earlier releases.
-   Introduced `FilePathUtils`.
