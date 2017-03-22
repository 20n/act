
20n/act predicts how to engineer biology
===

20n/act is the data aggregation and prediction system for bioengineering. Given a target molecule, it predicts what DNA insertions into a cell (usually a microbe such as _E. coli_ or _S. cerevisiae_) will allow the cell to make the target when fed sugar, i.e., by fermentation. We call these "target molecules/chemicals" the __bioreachables__. The system discovered that Acetaminophen can be bioproduced. Our [blog post](http://20n.com/blog.html#bio-acetaminophen) gives the overview, and the technical details are present in the patents applications covering coli and yeast fermentation.

Getting started
===
#### Live preview
See predicted DNA for 11 sample molecules at [Bioreachables Preview](https://preview.bioreachables.com/) (Login:Pass = public:preview). Due to limitations we can only make a preview version available. If you'd like the full version [please contact us](mailto:act@20n.com?subject=Purchase request for Enterprise Edition of Bioreachables Wiki).

#### Building the project
Checkout the repo. Follow [instructions to run](wikiServices#1-wiki-content-generation) to create the database and prediction corpus. If you'd rather get a pre-packaged DB without creating it yourself [please contact us](mailto:act@20n.com?subject=Purchase request for pre-packaged 20n/act DB.). The codebase is public to further the state-of-the-art in automating biological engineering/synthetic biology. Some modules are specific to microbes, but most of the predictive stack deals with host-agnostic enzymatic biochemistry.

Components of 20n/act
===

#### Predictor stack
Answers _"what DNA do I insert if I want to make my chemical?"_
  
  |   | Module | Function | Code |
  |---|---|---|---|
  | 1 | Installer | Integrates heterogeneous raw data | Code:[com.act.reachables.initdb](reachables/src/main/scala/initdb.scala) <br/>Run:[Instructions](wikiServices#create-an-act-db)
  | 2 | Reaction operator (RO) inference | Mines rules of enzymatic catalysis | Code:[biointerpretation module](reachables/src/main/java/com/act/biointerpretation)
  | 2 | Structure Activity Relationship (SAR) inference | Mines substrate specificities | Code:[biointerpretation module](reachables/src/main/java/com/act/biointerpretation)
  | 3 | Biointerpretation | Mechanistic validation of enzymatic transforms (using ROs) | Code:[com.act.biointerpretation.BiointerpretationDriver](reachables/src/main/java/com/act/biointerpretation/BiointerpretationDriver.java) <br> Run:[Instructions](wikiServices#run-biointerpretation)
  | 4 | Reachables computation | Exhaustively enumerates all biosynthesizable chemicals | Code:[com.act.reachables.reachables](reachables/src/main/scala/reachables.scala)<br>Code:[com.act.reachables.postprocess_reachables](reachables/src/main/scala/postprocess_reachables.scala)<br>Run:[Instructions](wikiServices#run-reachables-and-cascades)
  | 5 | Cascades computation | Exhaustively enumerates all enzymatic routes from metabolic natives to bioreachable target | Code:[com.act.reachables.cascades](reachables/src/main/scala/com/act/reachables/cascades.scala)<br>Run:[Instructions](wikiServices#run-reachables-and-cascades)
  | 6 | DNA designer | Computes protein & DNA design (coli specific) for each non-natural enzymatic path | Code:[org.twentyn.proteintodna.ProteinToDNADriver](reachables/src/main/java/org/twentyn/proteintodna/ProteinToDNADriver.java)<br>Run:[Instructions](wikiServices#building-dna-designs)
  | 7 | Application miner | Mines chemical applications using web searches [Bing] | Code:[act.installer.bing.BingSearcher](reachables/src/main/java/act/installer/bing/BingSearcher.java)<br>Run:[Instructions](wikiServices#augment-the-installer-with-bing-search-data)
  | 8 | Enzymatic biochemistry NLP | Text -> Chemical tokens -> Biologically feasible reactions using ROs | Code:[act.shared.TextToRxns](reachables/src/main/scala/act/shared/TextToRxns.scala)<br>Frontend:[TextToRxnsUI](reachables/src/main/r/TextToRxnsUI) |
  | 9 | Patent search | Chemical -> Patents | Code:[act.installer.reachablesexplorer.PatentFinder](reachables/src/main/java/act/installer/reachablesexplorer/PatentFinder.java)<br>Run:[Instructions](wikiServices#enrich-the-reachables-with-patents)
  | 9 | Bioreachables wiki | Aggregates reachables, cascades, use cases, protein and DNA designs into a user friendly wiki interface | [Documentation](wikiServices#2-new-wiki-instance-setup-steps)
  
  <p align="center"> <img width=65% src="http://20n.com/assets/video/making-apap-20n%3Aact-small.gif"> </p>

#### Analytics
Answers _"Is my bio-engineered cell doing what I want it to?"_  

  |   | Module | Function | Code |
  |---|---|---|---|
  | 1 | LCMS: Untargeted metabolomics | Deep-learnt signal processing to identify all chemical [side]effects of DNA engineering on cell | Code:[DeepLearningLcmsPeak](reachables/src/main/python/DeepLearningLcmsPeak)<br>Code:[com.act.lcms.UntargetedMetabolomics](reachables/src/main/scala/com/act/lcms/UntargetedMetabolomics.scala)
  | 2 | LCMS: Comparative visualization | Visualizing traces side-by-side from untargeted evaluation of over and underexpressed peaks | Doc:[LCMSDataVisualisation](reachables/src/main/r/LCMSDataVisualisation)

  <p align="center"> <img width=65% src="http://20n.com/assets/img/lcms-viz.png"> </p>
  
#### Unit economics of bioproduction
Answers _"Can I use bio-production to make this chemical at scale?"_  

  |   | Module | Function | Code
  |---|---|---|---|
  | 1 | Cost model: Manufacturing unit economics for large scale production | It backcalculates cell efficiency (yield, titers, productivity) objectives based on given COGS ($ per ton) of target chemical. From cell efficiency objectives it guesstimates the R&D investment (money and time) and ROI expectations | Code:[act.installer.bing.CostModel](reachables/src/main/scala/costmodel.scala)<br>Code (viz server):[costModelUI](reachables/src/main/r/costModelUI)<br>Source model:[XLS](http://20n.com/assets/spreadsheet/cost-model.xlsx)

License and Contributing
===
Code licensed under the GNU General Public License v3.0.
If an alternative license is desired, [please contact 20n](act@20n.com).

Original Authors
===
* [Saurabh Srivastava](http://saurabh-srivastava.com/)
* [J. Christopher Anderson](https://www.linkedin.com/in/j-christopher-anderson-682b0594/)
* Mark T. Daly
* Michael Lampe
* Thomas Legrand
* Vijay Ramakrishnan
* Gil Goldshlager
* Nishant Kakar

