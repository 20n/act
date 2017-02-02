## Odd Sequences to Protein Prediction Flow

### _Goal_
This workflow utilizes the HMMer utility to attempt to infer the actual sequence (Based on reference proteomes) 
of a sequence described as odd [1].

_[1] While the definition of odd may vary based on the exact implementation in `OddSequencesToProteinPredictionFlow`, the definition at the time of writing is_

_1) Sequence does not start with M (Methionine, this is a biochemical property) **OR**_

_2) Sequence length is less than 80 (Arbitrarily chosen to attempt to detect sequence fragments) **OR**_

_3) Sequence contains the `*` character (Used by the FASTA format to indicate wildcard characters)_

### _How it works_
#### 1) Reference Proteome Indexing
Creates an index of the reference proteomes based on the organism that they match.

#### 2) For each sequence
1) Concatenates the reference proteomes that match the organism of the sequence.

2) Compares the current sequence against the reference sequences based on the HMMer protocol.

3) Outputs a ranked list of the HMMer results, based on the score assigned by HMMer.

4) Modify the original sequence entry to contain additional metadata regarding the inferred sequences.

### Use
Can run as a stand-alone module by running the class `OddSequencesToProteinPredictionFlow` and using 
the relevant command line interface to guide you.

Additionally, the public methods can be used elsewhere, as shown in the `Cascades` class.

