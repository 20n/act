package com.act.biointerpretation.metadata;

public enum Localization {
    unknown, //No information is known
    questionable,  //Information is available suggesting ambiguity about where the protein will go

    //Bacterial localization
    cytoplasm,
    inner_membrae,
    periplasm,
    outer_membrane,
    secreted,

    //Eukaryotic localization
    endoplasmid_reticulum,
    Golgi,
    mitochondria,
    nucleus,
    lysozome,

    //Animal localization

    //Plant localization
    plastid,
    chloroplast
}
