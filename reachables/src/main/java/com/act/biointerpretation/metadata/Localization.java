/*************************************************************************
*                                                                        *
*  This file is part of the 20n/act project.                             *
*  20n/act enables DNA prediction for synthetic biology/bioengineering.  *
*  Copyright (C) 2017 20n Labs, Inc.                                     *
*                                                                        *
*  Please direct all queries to act@20n.com.                             *
*                                                                        *
*  This program is free software: you can redistribute it and/or modify  *
*  it under the terms of the GNU General Public License as published by  *
*  the Free Software Foundation, either version 3 of the License, or     *
*  (at your option) any later version.                                   *
*                                                                        *
*  This program is distributed in the hope that it will be useful,       *
*  but WITHOUT ANY WARRANTY; without even the implied warranty of        *
*  MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the         *
*  GNU General Public License for more details.                          *
*                                                                        *
*  You should have received a copy of the GNU General Public License     *
*  along with this program.  If not, see <http://www.gnu.org/licenses/>. *
*                                                                        *
*************************************************************************/

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
