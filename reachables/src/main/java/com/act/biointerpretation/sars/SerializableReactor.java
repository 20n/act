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

package com.act.biointerpretation.sars;

import chemaxon.formats.MolExporter;
import chemaxon.reaction.ReactionException;
import chemaxon.reaction.Reactor;
import com.fasterxml.jackson.annotation.JsonIgnore;
import com.fasterxml.jackson.annotation.JsonProperty;

import java.io.IOException;
import java.io.Serializable;

public class SerializableReactor implements Serializable {
  private static final long serialVersionUID = 6798168677299741769L;
  private static String SERIALIZATION_FORMAT = "smarts";

  @JsonProperty("ro_id")
  private Integer roId;

  @JsonIgnore
  private Reactor reactor;

  /**
   * For json.
   */
  private SerializableReactor() {
  }

  public SerializableReactor(SerializableReactor template) {
    this.roId = template.roId;
    this.reactor = template.reactor;
  }

  public SerializableReactor(Reactor reactor, Integer roId) {
    this.reactor = reactor;
    this.roId = roId;
  }

  @JsonProperty("reactor_smarts")
  public String getReactorSmarts() throws IOException {
    return MolExporter.exportToFormat(reactor.getReaction(), SERIALIZATION_FORMAT);
  }

  @JsonProperty("reactor_smarts")
  private void setReactorSmarts(String smarts) throws ReactionException {
    reactor = new Reactor();
    reactor.setReactionString(smarts);
  }

  @JsonIgnore
  public Reactor getReactor() {
    return reactor;
  }

  public Integer getRoId() {
    return roId;
  }
}
