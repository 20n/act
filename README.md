20n/act predicts what biology can help us manufacture
===

20n/act is the data aggregation and prediction system for bioengineering applications, specifically fermentation-based bio-manufacturing. Given any target chemical to be bioproduced, it can tell you what DNA to insert into a cell (usually a microbe such as _E. coli_ or _S. cerevisiae_) so that it makes the target chemical when fed sugar, i.e., by fermentation. We call these "target chemicals" the __bioreachables__. One surprising discovery from this system was the bioproduction of Acetaminophen (blog post, patents covering coli fermentation, yeast fermentation and separation).

Components of 20n/act:
===

* Predictions (what DNA do I insert if I want to make my chemical?):
  * Installer: Integrates heterogeneous raw data
  * RO inference: Derives rules of enzymatic biochemistry from known evidence
  * Biointerpretation: Mechanistic validation of enzymatic transforms
  * Reachables computation: Exhaustively enumerates all biosynthesizable chemicals
  * Cascades computation: Exhaustively enumerates all enzymatic routes from metabolic natives to bioreachable target
  * DNA computation: Assigns DNA design to each non-natural enzymatic path
  * Application miner: Mines chemical applications using web searches [Bing]
  * Bioreachables wiki: Aggregates reachables, cascades, use cases, protein and DNA designs into a user friendly wiki interface
* Analytics (Is my bio-engineered cell doing what I want it to?):
  * LCMS (untargeted metabolomics): Deep-learnt signal processing to identify all chemical [side]effects of DNA engineering on cell
* Unit economics of bioproduction (Can I use bio-production to make this chemical at scale?):
  * Cost model: Manufacturing unit economics for large scale production. It backcalculates cell efficiency (yield, titers, productivity) objectives based on given COGS ($ per ton) of target chemical. From cell efficiency objectives it guesstimates the R&D investment (money and time) and ROI expectations.

Documentation Entrypoints
===

* Wiki setup: https://github.com/20n/act/tree/master/wikiServices
* Creating azure servers: https://github.com/20n/act/wiki/Architecture:-On-Azure
* Azure administration: https://github.com/20n/act/tree/master/scripts/azure
* After reboot setup:  https://github.com/20n/act/wiki/After-Reboot-Startup-Checklist
* DNS + NGINX: https://github.com/20n/act/wiki/DNS-and-NGINX
* VPN Access:  https://github.com/20n/act/wiki/OpenVPN
