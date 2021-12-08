# All About The FlowGraph

The "Flow Graph" is how Pipeline stores structural information about Pipelines that have run or *are* running -- this is used for visualization and runtime activities. You won't find any class with this name but we refer to it like this because it's the best terminology we have.  "Flow" because originally the plugin was Workflow, and describing it as a flow reminds us that execution is a directional process with a start and end, "graph" because the data is structured as a directed acyclic graph to allow for parallel execution.

This structure describes all possible Pipelines, whether a simple set of steps, or a complex set of nested parallels, and is designed to grow at runtime as the Pipeline executes. This also means that for a running Pipeline it will be in an incomplete state, with un-terminated blocks.

# Concepts

The FlowGraph is a directed acyclic graph, but we should think of it as being backward-linked because we only store the IDs of its parents (the nodes that precede it).  In the `FlowExecution` we only store a reference to the **head** `FlowNode` IDs - these represent the most current steps that are running.   To find the start of the graph, you need to interate from one of the heads to the first `FlowNode`, which will be a `FlowStartNode.`

This may seem backwards, but it enables us to freely append to the Flow Graph as the Pipeline executes and we provide some helpful caching. 

**Key information is stored in a couple ways:**

* The specific subclass of the `FlowNode` used for a particular node is structurally important - for example `BlockStartNode` represents the start of a block
* `StepDescriptor`: most `FlowNode`s come from running `Steps` and implement `StepNode`. This means they can get the `StepDescriptor` to determine their `Step` that produced them.
* Parent relationships allow us to so split into parallel branches and track them independently.  We may have multiple nodes with a given parent (at the start of a parallel) or one node with multiple parents (the end of a parallel)
* `Action`s give us all the key attributes of the `FlowNode`, see the section below for a quick reference

# Structural elements

* `Step` - documented elsewhere, a single Pipeline action, i.e. running a shell command, reading a file, obtaining an Executor, or binding an environment variable into context
* `FlowNode` - this is the actual class.  One or more flownodes is created for every Pipeline step run.
* Atomic FlowNode, i.e. `StepAtomNode` class - a `FlowNode` that maps to a single `Step` being executed
* Block - a block has a distinct `BlockStartNode` and `BlockEndNode`, and may contain other `FlowNodes` within it. 
    - The `BlockEndNode` stores the id of the `BlockStartNode` and you can retrieve the start via `#getStartNode()`
    - Every flow is enclosed in a `FlowStartNode` and `FlowEndNode` block -- these are special `BlockStartNode` and `BlockEndNode` types with no corresponding steps. 
    - Most commonly blocks are created by a step that takes a closure and can contain other steps.  I.E. `node` and `withEnv`, etc
    - Block steps will generate at least 2 blocks, an outer block for running the step itself, and one or more inner blocks for the contents of the block (the "body"). The inner blocks will be marked with a `BodyInvocationAction`, and multiple inner blocks can be created if the body is evaluated multiple times (ex: `retry` steps).

# Key Actions And Where They're Used

* `ErrorAction` - stores an error that occurred within a `FlowNode`, or for `BlockEndNodes`, happened within the block and was not caught by a try/catch block
* `TimingAction` gives the timestamp when a `FlowNode` was created - we don't store duration because this can be calculated by subtracting from the timestamp for the next `FlowNode`
* `LabelAction` - attaches a name to a `FlowNode`. Common examples: stage and checkpoint. Parallel branches have a `ParallelLabelAction` which extends `LabelAction` and `ThreadNameAction` to tell us the name of each branch
* `BodyInvocationAction` - attached to the start and end of the "inner" block where the contents of a block-scoped step is evaluated
* `LogAction` - subclasses provide a way to fetch the build log sections associated with a `FlowNode`
* `ArgumentsAction`, implemented via `ArgumentsActionImpl` - provides information about the user-supplied arguments given to run a step, for example so you can see the actual shell command executed (secrets will be masked)
* `NotExecutedNodeAction` - attached when we start a Pipeline by resuming from a checkpoint, used to mark the `FlowNodes` preceding the checkpoint, which were not actually executed this time 
* `WorkspaceAction` - used where we are obtaining a workspace on an executor to run some part of the Pipeline

# Key Domain Knowledge
* Parallels create a block for each branch, plus an enclosing block
* Stages have a legacy atom step syntax (rarer), plus a more modern block-scoped syntax and flow graphs may technically include both

# Working with the FlowGraph

* See the methods on `FlowNode` for starters
* Implementations of `GraphListener` can be used to listen as new `FlowNode`s are created and stored
* We provide a quick lookup method to find or iterate over enclosing blocks for a `FlowNode`, with caching of results, via `FlowNode#getEnclosingId`, `FlowNode#iterateEnclosingBlocks` (returns an iterable with lazy fetching),
    * These have eager-fetch equivalents, i.e. `FlowNode#getEnclosingBlocks()` and `FlowNode#getAllEnclosingIds()` but you should use them with caution since you may have to walk over the the entire flow graph to get this data. 
    * These methods are implemented under the `FlowExecution` itself
* The Pipeline Graph Analysis plugin gives us automatic methods to use in finding the time a `FlowNode` spent executing and the visual 'status' to assign to it.  This process is more complex than it will seem -- it is better to look at the code than to make assumptions here.
* We have a library of graph analysis tools in the workflow-api plugin to make it easier to work with the Flow Graph, see the `org.jenkinsci.plugin.workflow.graphanalysis` package
    - The package-info.java file here includes a ton of documentation on how this works
    - In general these methods are based on two methods: implementations of `AbstractFlowScanner` provide iteration over the flow graph in defined orders, and `FlowNodeVisitor` can be handed to these to perform an operation on each node in the iteration
        - The different Flow Scanner implementations have different iteration orders, and may skip some nodes (such as LinearBlockHoppingScanner) - see the package-info.java file for details
        - In general you want the `DepthFirstFlowScanner` if you want to visit all the nodes
        - Use a `LinearFlowScanner` if you want to just want along one branch to get to the start of the Flow Graph
        - The scanners are also `Iterable` and can be used to create filterators by supplying a Guava predicate
    - The class `FlowScanningUtils`, and the `NodeStepNamePredicate` can be really helpful to filter the flow graph for specific pieces of information
* Storage: in the workflow-support plugin, see the 'FlowNodeStorage' class and the `SimpleXStreamFlowNodeStorage` and `BulkFlowNodeStorage` implementations
    - **`FlowNodeStorage` uses in-memory caching to consolidate disk writes. Automatic flushing is implemented at execution time. Generally you won't need to worry about this, but be aware that saving a `FlowNode` does not guarantee it is immediately persisted to disk.**
    - The `SimpleXStreamFlowNodeStorage` uses a single small XML file for every `FlowNode` -- although we use a soft-reference in-memory cache for the nodes, this generates much worse performance the first time we iterate through the `FlowNodes` (or when)
    - The `BulkFlowNodeStorage` uses a single larger XML file with all the `FlowNode`s in it.  This is used in the `PERFORMANCE_OPTIMIZED` durability mode, which writes much less often.  It is generally much more efficient because a single large streaming write is faster than a bunch of small writes, and it minimizes the system load of managing all the tiny files.

# Gotchas

* For running Pipelines you will have `BlockStartNode`s for the blocks that are currently unfinished but WILL NOT have a `BlockEndNode`, since the current heads of the Flow Graph will be inside the block
* It is completely legal to have stages inside parallels inside stages and parallels inside parallels, or any recursive combination of the same.  Complex combinations (especially parallels inside parallels) are less common but some actual users are doing this in the wild.  We know because we get bug reports related to parallels in parallels.
* `FlowNode` datafiles can be corrupted or unreadable on disk in some rare circumstances.  This may prevent iteration.  Be aware that it can happen, and provide defined error handling (don't just swallow exceptions).
* Not all `FlowNodes` are `StepNodes`: the `FlowStartNode` and `FlowEndNode` that start and end execution do not have a corresponding step.  They may not have a `TimingAction` either (early bug)
* There are gaps in the ID numbers for `FlowNodes`: when using parallels, IDs will skip some numbers.  This means 2, 3, 4, 9, 10, 11, etc is a completely legal sequence and will really occur.
* There may be multiple **head** flownodes, if we are currently running parallel branches
* **There are no bounds on the size of the flow graph, it may have thousands of nodes in it.**  Real users with complex Pipelines will really generate graphs this size.  Yes, really.  
* **Repeat: there are no bounds on the size of the flow graph.  This means if you use recursive function calls to iterate over the Flow Graph you will get a `StackOverFlowError`!!!**  Use the `AbstractFlowScanner` implementations - they're free of stack overflows and well-tested.
* As a back of napkin estimate, most Flow Graphs fall in the 200-700 `FlowNode` range
* `GraphListener` gotcha: because the listener is invoked for each new `FlowNode`, if you implement some operation that iterates over a lot of the Flow Graph then you've just done an O(n^2) operation and it can result in very high CPU use.  This can bog down a Jenkins controller if not done carefully.
    - Careful use of the methods above to iterate/find enclosing flownodes can make this much safer


# Anti-gotchas

* It may seem intimidating but the Flow Graph has a TON of useful information ripe for analysis and visualization!
* The gotchas above are mostly a problem for plugins that try to manipulate the Flow Graph or do automated analysis while Pipelines are running -- if you use the utilities and wait for Pipelines to complete then most of the problems go away.
* The actual data model is quite simple.
