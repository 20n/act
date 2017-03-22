20n/act/reachables
===
This project enumerates all bio-accessible chemicals. For each of those chemicals, it will design DNA blueprints. These DNA blueprints can bioengineering organisms with un-natural function.

20n/act/reachables contains many modules built from scratch in-house. Some: mine raw biochemical data, integrate heterogenous sources, learn rules of biochemistry, automatically clean bad data, mine patents, mine plain text, bioinformatic identification of enzymes with desired function.
  <p align="center"> <img width=65% src="http://20n.com/assets/img/bio-reachable-chemicals.jpg"> </p>


Data integration, biochemical model, predictions, DNA design
===
This project holds most of the capabilities of 20n/act:
* Data integrator
  * Integrates various heterogenous data sources containing enzyme function observations: BRENDA, MetaCyc, KEGG.
  * Chemical applications: Mines wikipedia, BING, ChEBI.
* Biochemical model (chemoinformatics style)
  * Reaction operators (ROs): Model catalytic activity of enzymes
  * Structure activity relationships (SARs): Model substrate specificity
  * Inference of ROs and SARs
  * Uses of RO+SAR: Mechanistic validation of observations, prediction of new enzymatic chemistry
* DNA design
  * Chemical to non-natural pathway (protein sequences and chemical intermediates)
  * Non-natural pathway to DNA design for microbial engineering (_E. coli_ specific)
* Untargeted metabolomics (LCMS analysis): Deep learning based automatic identification of comparative cellular changes
* Bioinformatics: Infer enzymes with specified biochemical function (better than EC classifications)

Few modules outside of this project:
* Front-end: Bio-reachables wiki that provides a human-accessible interface to all reachable chemicals, and their DNA designs.
* Bioreactor: Preliminary designs for a home-grown DIY bio-reactor. Commercial versions cost upwards of $60,000. DIY bioreactors could be built for under $5,000 in total parts.
