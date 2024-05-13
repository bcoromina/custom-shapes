# custom-shapes


MapAsyncErrorUnordered

This shape behaves like .mapAsyncUnordered but in case that Future fails it places the Throwable on a different output 
so it can be handled by a different flow.

In MapAsyncErrorUnorderedSpec there is an example of usage where error output is processed with error handling and 
merged with the success output.