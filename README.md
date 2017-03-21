
20n/act predicts how to engineer biology
===

20n/act is the data aggregation and prediction system for bioengineering. Given a target molecule, it predicts what DNA insertions into a cell (usually a microbe such as _E. coli_ or _S. cerevisiae_) will allow the cell to make the target when fed sugar, i.e., by fermentation. We call these "target molecules/chemicals" the __bioreachables__. The system discovered that Acetaminophen can be bioproduced. Our [blog post](http://20n.com/blog.html#bio-acetaminophen) gives the overview, and the technical details are present in the patents applications covering coli and yeast fermentation.

Getting started
===
#### Live preview
See predicted DNA for 11 sample molecules at [Bioreachables Preview](https://preview.bioreachables.com/) (Login:Pass = public:preview). Due to limitations we can only make a preview version available. If you'd like the full version [please contact us](mailto:info@20n.com).

#### Building the project
Checkout the repo. Follow [instructions to run](wikiServices#1-wiki-content-generation). The codebase is public to further the state-of-the-art in automating biological engineering/synthetic biology. Some modules are specific to microbes, but most of the predictive stack deals with host-agnostic enzymatic biochemistry.

Components of 20n/act
===

#### Predictor stack
Answers _"what DNA do I insert if I want to make my chemical?"_
  
  |   | Module | Function | Code |
  |---|---|---|---|
  | 1 | Installer | Integrates heterogeneous raw data | [com.act.reachables.initdb](reachables/src/main/scala/initdb.scala) with [doc](wikiServices#create-an-act-db)
  | 2 | Reaction operator (RO) inference | Mines rules of enzymatic biochemistry from observations |
  | 3 | Biointerpretation | Mechanistic validation of enzymatic transforms (using ROs) |
  | 4 | Reachables computation | Exhaustively enumerates all biosynthesizable chemicals |
  | 5 | Cascades computation | Exhaustively enumerates all enzymatic routes from metabolic natives to bioreachable target |
  | 6 | DNA designer | Computes protein & DNA design (coli specific) for each non-natural enzymatic path |
  | 7 | Application miner | Mines chemical applications using web searches [Bing] |
  | 8 | Enzymatic biochemistry NLP | Text -> Chemical tokens -> Biologically feasible reactions using ROs: [PR:text-to-rxns](https://github.com/20n/act/pull/525) |
  | 9 | Bioreachables wiki | Aggregates reachables, cascades, use cases, protein and DNA designs into a user friendly wiki interface |
  
  <p align="center"> <img width=65% src="http://20n.com/assets/video/making-apap-20n%3Aact-small.gif"> </p>

#### Analytics
Answers _"Is my bio-engineered cell doing what I want it to?"_  

  |   | Module | Achieves |
  |---|---|---|
  | 1 | LCMS: untargeted metabolomics | Deep-learnt signal processing to identify all chemical [side]effects of DNA engineering on cell |
  
#### Unit economics of bioproduction
Answers _"Can I use bio-production to make this chemical at scale?"_  

  |   | Module | Achieves |
  |---|---|---|
  | 1 | Cost model: Manufacturing unit economics for large scale production | It backcalculates cell efficiency (yield, titers, productivity) objectives based on given COGS ($ per ton) of target chemical. From cell efficiency objectives it guesstimates the R&D investment (money and time) and ROI expectations |

License and Contributing
===
Code licensed under the GNU General Public License v3.0.
If an alternative license is desired, [please contact 20n](act@20n.com).

