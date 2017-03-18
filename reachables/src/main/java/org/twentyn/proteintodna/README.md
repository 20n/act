
The code in this directory is badly formatted. It needs to be reformated to "2 space" convention.
E.g., with vim: %s;^\(\s\+\);\=repeat(' ', len(submatch(0))/2);g
From https://gist.github.com/ericdouglas/72621cb47b368297feaa

Deferring to later. Currently, github cannot recognize the changes are pure formatting changes and shows large differences between the files. That will muck up history and blame assignment. Leave it to later.
