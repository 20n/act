20n/act predicts what biology can help us manufacture
===

20n/act is the data aggregation and prediction system for bioengineering applications, specifically fermentation-based bio-manufacturing. Given any target chemical to be bioproduced, it can tell you what DNA to insert into a cell (usually a microbe such as _E. coli_ or _S. cerevisiae_) so that it makes the target chemical when fed sugar, i.e., by fermentation. We call these "target chemicals" the __bioreachables__. One surprising discovery from this system was the bioproduction of Acetaminophen (blog post, patents covering coli fermentation, yeast fermentation and separation).

Getting started
===
* Those who want the data (biologists, maybe?): Use login:pass as public:preview at [Bioreachables Preview](https://preview.bioreachables.com/). Due to limitations we can only make a preview version available for public use. If you'd like the full version [please contact us](mailto:info@20n.com).
* Those who want the code (softwarers, maybe?): Checkout the repo and follow [Instructions to run](https://github.com/20n/act/tree/master/wikiServices#1-wiki-content-generation)

Components of 20n/act
===

#### Predictor stack
Answers _"what DNA do I insert if I want to make my chemical?"_
  
  |   | Module | Achieves |
  |---|---|---|
  | 1 | Installer | Integrates heterogeneous raw data |
  | 2 | RO inference | Derives rules of enzymatic biochemistry (reaction operators = RO) from known evidence |
  | 3 | Biointerpretation | Mechanistic validation of enzymatic transforms |
  | 4 | Reachables computation | Exhaustively enumerates all biosynthesizable chemicals |
  | 5 | Cascades computation | Exhaustively enumerates all enzymatic routes from metabolic natives to bioreachable target |
  | 6 | DNA designer | Computes protein & DNA design (coli specific) for each non-natural enzymatic path |
  | 7 | Application miner | Mines chemical applications using web searches [Bing] |
  | 8 | Bioreachables wiki | Aggregates reachables, cascades, use cases, protein and DNA designs into a user friendly wiki interface |
  
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

Documentation Entrypoints
===

* Wiki setup: https://github.com/20n/act/tree/master/wikiServices
* Creating azure servers: https://github.com/20n/act/wiki/Architecture:-On-Azure
* Azure administration: https://github.com/20n/act/tree/master/scripts/azure
* After reboot setup:  https://github.com/20n/act/wiki/After-Reboot-Startup-Checklist
* DNS + NGINX: https://github.com/20n/act/wiki/DNS-and-NGINX
* VPN Access:  https://github.com/20n/act/wiki/OpenVPN
