After signal processing (e.g., using deep learning) such as in [bucketed_differential_deep.py](reachables/src/main/python/DeepLearningLcmsPeak) over raw LCMS traces, run the code here to generate a differential peak detection algorithm.

```
$ sbt "runMain com.act.lcms.UntargetedMetabolomics"
 Loading project definition from /Users/saurabhs/act-private/reachables/project
 Set current project to reachables (in build file:/Users/saurabhs/act-private/reachables/)
 Running com.act.lcms.UntargetedMetabolomics -h
 usage: com.act.lcms.UntargetedMetabolomics$ [-c <{name=file}*>] [-C] [-D <layoutJson=diffData>] [-e
        <{name=file}*>] [-F <file>] [-h] [-I] [-M] [-o <filename>] [-R <[low-high,]+>] [-S <file>]
        [-Z <M>]
  -c,--controls <{name=file}*>                                     Controls: Comma separated list of
                                                                   name=file pairs
  -C,--map-to-formula-using-solver                                 Fallback to SMT Constraint solver
                                                                   to solve formula. Ideally, the
                                                                   lower MW formulae would have been
                                                                   enumerated in lists, and so would
                                                                   be matched using lookup but for
                                                                   higher MW, e.g., where CHNOPS
                                                                   counts are > 30 we use the solver
  -D,--get-differential-from-deep-learning <layoutJson=diffData>   Instead of doing the
                                                                   ratio_lines(min_replicates(max_sam
                                                                   ple)) manually, we can take the
                                                                   input differential from deep
                                                                   learning. The input here takes the
                                                                   form of jsonfile=differentialData,
                                                                   where jsonfile contains the
                                                                   original 01.nc filenames and
                                                                   differentialData is the called
                                                                   differential peaks from deep
                                                                   learning.
  -e,--experiments <{name=file}*>                                  Experiments: Comma separated list
                                                                   of name=file pairs
  -F,--formulae-using-list <file>                                  An enumerated list of formulae
                                                                   (from formulae enumeration, or
                                                                   EnumPolyPeptides). TSV file with
                                                                   one formula per line, and
                                                                   optionally tab separated column of
                                                                   monoisotopic mass for that
                                                                   formula. If missing it is computed
                                                                   online. The TSV hdrs need be
                                                                   `formula` and `mol_mass`
  -h,--help                                                        Prints this help message
  -I,--convert-ion-mzs-to-masses                                   If flag set, each mz is translated
                                                                   to {(ion, mass)}, where mass is
                                                                   the monoisotopic mass of the
                                                                   molecule whose candidate ion got a
                                                                   hit
  -M,--rank-multiple-ions-higher                                   If flag set, then each if a mass
                                                                   has multiple ions with hits, it
                                                                   ranks higher
  -o,--outjson <filename>                                          Output json of peaks, mz, rt,
                                                                   masses, formulae etc.
  -R,--filter-rt-regions <[low-high,]+>                            A set of retention time regions
                                                                   that should be ignored in the
                                                                   output
  -S,--structures-using-list <file>                                An enumerated list of InChIs (from
                                                                   HMDB, RO (L2n1-inf), L2). TSV file
                                                                   with one inchi per line, and
                                                                   optionally tab separated column of
                                                                   monoistopic mass. TSV hdrs need be
                                                                   `inchi` and `mol_mass`
  -Z,--restrict-ions <M>                                           Only consider limited set of ions,
                                                                   e.g., M+H,M+Na,M+H-H2O,M+Li,M+K.
                                                                   If this option is omitted system
                                                                   defaults to M+H,M+Na,M+K
```

Example run:
```
$ sbt "runMain com.act.lcms.UntargetedMetabolomics -I -M \
-S L2inchis.named \
-D differential_expression_run_summary.txt=differential_expression.tsv \
-N small-formulae-stable.tsv \
-o lcms-differentials.json"
```
