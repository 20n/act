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

package org.twentyn.proteintodna;

import java.util.List;

public class Mrna {
    public RBSOption rbs;
    public String peptide;
    public int[] codons;
    
    public String toSeq() throws Exception {
        CodonIndexer indexer = CodonIndexer.initiate();
        
        StringBuilder out = new StringBuilder();
        for(int i=0; i<peptide.length(); i++) {
            char aa = peptide.charAt(i);
            List<String> codonList = indexer.aminoAcidToBestCodons.get(aa);
            out.append(codonList.get(codons[i]));
        }
        out.append("TAA");
            
        return rbs.rbs.toLowerCase() + out.toString().toUpperCase();
    }
}
