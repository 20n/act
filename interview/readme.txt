Questions:
Q1. Coding: Take graphs.py and find best AAM. Of course it is subgraph isomorphism, but given assumptions, find write heuristics that do well.

Q2. Whiteboard: Molecular graphs can have "rings" -- i.e., cycles in the graph representation. 1. Find all minimal non-overlapping rings, 2. Create map of ring#->atom ids that make up that ring.

Q3. Take home: Maximal common subgraphs: infer a map from a reaction: mol_id -> atom_id -> mapping_index; such that corresponding atoms on either side of the reaction have identical mapping_index.
  - sbt "runMain interview.subgraph src/test/test-rxns.txt"
  - will create {custom_rxn_, library_rxn_, pretty_rxn_}.png
