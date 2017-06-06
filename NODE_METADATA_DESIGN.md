## Design proposal for additional node metadata - [JENKINS-26522](https://issues.jenkins-ci.org/browse/JENKINS-26522)

### Problem statement

Before this change, `FlowNode`s only can have a limited amount of metadata. For example, status is simply determined by
whether there is an `ErrorAction` on the `FlowNode` - if so, it's considered failed, otherwise, it's considered passed.
There's no way to record or report on more granular status, such as `UNSTABLE` or `ABORTED` for an individual `FlowNode`
(or block/stage). There also is no way to allow plugins to contribute additional metadata to `FlowNode`s, such as test
results, so there's no way to report on which particular step or stage contributed which test results. Combined, this
limits the reporting and visualization capabilities available to Blue Ocean and the Pipeline Stage View significantly.

### Summary of Design

To address this, we will use a new `PersistentAction` similar to `TagsAction` to record that needed additional metadata.
This new action, named something like `PropagatingTagsAction` to signify that it plays a similar role to `TagsAction`, 
with the noteworthy difference that its tags are propagated to enclosing blocks. Status in particular will be 
special-cased in `PropagatingTagsAction` to enforce the rule that status can only get worse, not better. 

`PropagatingTagsAction` will support multiple `String` values for a single tag, to support cases such as aggregating 
test results from nested steps and blocks. While using a single `String` value with delimiters has been considered, 
a `List` or `Set` of `String`s avoids potential pitfalls with use of a delimiter character in a tag value. Also, use of
multiple tags with a common prefix has been considered, but could have problems due to uniqueness of the suffixes 
across nested steps and blocks, as well as the pain of having to discover all tags with the relevant prefix but not a
different prefix that contains the first prefix, etc.

The `PropagatingTagsAction` public API will look like the following:
* `void addTag(String, String)` - add a new tag with a single `String` value, overwriting any existing value for the tag.
* `void addTag(String, Collection<String>)` - add a new tag with its value set to the contents of the given `Collection`, 
overwriting any existing value for the tag.
* `void removeTag(String)` - remove an existing tag and its value.
* `void appendToTag(String, String)` - append a new `String` to the existing value for the given tag. If the tag is not 
already present, it will be added.
* `void appendToTag(String, Collection<String>)` - append all the contents of the given `Collection` to the existing 
value for the given tag. If the tag is not already present, it will be added.
* `void propagateTags(PropagatingTagsAction)` - propagates all tags from the given `PropagatingTagsAction` to this one,
following status rules, etc.
* `Set<String> getTagValue(String)` - get the value for a given tag. If the tag does not exist, this will return an 
empty set.
* `Map<String,Set<String>> getTags()` - returns a copy of the underlying map of tags.
* `boolean setStatus(Result)` - sets the tag for `FlowNode` status if and only if it's not already set to a worse status 
than the provided result. Returns true if it was able to set the status, false otherwise.
* `boolean setStatus(String)` - calls `boolean setStatus(Result)` after calling `Result.fromString(String)` on the provided 
`String`. Throws an exception if it can't convert the `String` to a valid `Result`.
* `Result getStatus()` - returns the `Result.fromString(String)` value for the status tag on this `FlowNode`. If not set,
will return `Result.SUCCESS`.

### TODO

* Determine how to get the propagated tags to the relevant `BlockEndNode`. 