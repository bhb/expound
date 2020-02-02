# Project Update 3: 2020-01-01 - 2020-01-15


#### Improving the display of "context" values

Expound error messages display the specific problem within the context of the entire invalid value. For instance, if `"456"` should be an integer within `{:ids [123 "456" 789]}`, Expound will print:

```
  {:ids [... "456" ...]}
             ^^^^^

should satisfy

  int?
```  

There are a number of unresolved issues in Expound that all relate to how Expound prints the "context" value - either by showing too much information (which creates very long error messages) or showing too little (which hides important information about where an invalid value is located).

I've been doing a lot of design work (writing an ADR and spiking some code) on how I might resolve such issues.

#### spec-alpha2 support

Separately, I've been beginning to lay some early foundations for `expound.alpha2` which will support `spec-alpha2`

* Created a new Expound namespace
* Created very basic test that old and new namespaces can be loaded with the corresponding version of spec
* Read up on `spec-alpha2` documentation