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

package com.act.reachables;

import com.fasterxml.jackson.annotation.JsonIgnore;
import com.fasterxml.jackson.annotation.JsonProperty;

import java.util.ArrayList;
import java.util.List;
import java.util.stream.Collectors;

public class ReactionPath {

    public List<Cascade.NodeInformation> getPath() {
        return path;
    }

    public void setPath(List<Cascade.NodeInformation> path) {
        this.path = path;
    }

    public String getId() {
        return id;
    }

    public void setId(String id) {
        this.id = id;
    }

    public Long getTarget() {
        return target;
    }

    public void setTarget(Long target) {
        this.target = target;
    }

    public Long getRank() {
        return rank;
    }

    public void setRank(Long rank) {
        this.rank = rank;
    }

    public Integer getDegree() {
        return degree;
    }

    public void setDegree(Integer degree) {
        this.degree = degree;
    }

    public Integer getReactionSum() {
        return reactionSum;
    }

    public void setReactionSum(Integer reactionSum) {
        this.reactionSum = reactionSum;
    }
    
    public void setDnaDesignRef(String id) {
        this.dnaDesignRef = id;
    }

    public String getDnaDesignRef() {
        return this.dnaDesignRef;
    }

    private List<Cascade.NodeInformation> path;

    @JsonProperty("_id")
    private String id;

    private Long target;
    private Long rank;
    private Integer degree;
    private Integer reactionSum;
    private Boolean isMostNative = false;
    private String dnaDesignRef;

    public ArrayList<String> getMostCommonOrganism() {
        return mostCommonOrganism;
    }

    public void setMostCommonOrganism(ArrayList<String> mostCommonOrganism) {
        this.mostCommonOrganism = mostCommonOrganism;
    }

    public ArrayList<Double> getMostCommonOrganismCount() {
        return mostCommonOrganismCount;
    }

    public void setMostCommonOrganismCount(ArrayList<Double> mostCommonOrganismCount) {
        this.mostCommonOrganismCount = mostCommonOrganismCount;
    }

    public Boolean getMostNative() {
        return isMostNative;
    }

    public void setMostNative(Boolean mostNative) {
        isMostNative = mostNative;
    }

    private ArrayList<String> mostCommonOrganism;
    private ArrayList<Double> mostCommonOrganismCount;

    public ReactionPath() {}

    public ReactionPath(String id, List<Cascade.NodeInformation> path) {
        this.id = id;
        this.path = path;
        this.target = Long.parseLong(this.id.split("w")[0]);
        this.rank = Long.parseLong(this.id.split("w")[1]);
        this.degree = this.getReactionCount(this.path.get(1));
        this.reactionSum = this.path.stream().map(x -> getReactionCount(x)).collect(Collectors.summingInt(Integer::intValue));
    }

    public ReactionPath(List<Cascade.NodeInformation> path, String id, Long target, Long rank, Integer degree, Integer reactionSum, Boolean isMostNative, ArrayList<Double> mostCommonOrganismCount, ArrayList<String> mostCommonOrganism) {
        this.path = path;
        this.id = id;
        this.target = target;
        this.rank = rank;
        this.degree = degree;
        this.reactionSum = reactionSum;
        this.isMostNative = isMostNative;
        this.mostCommonOrganism = mostCommonOrganism;
        this.mostCommonOrganismCount = mostCommonOrganismCount;
    }

    @JsonIgnore
    private Integer getReactionCount(Cascade.NodeInformation nodeInfo) {
        // Only reactions contribute
        return nodeInfo.getReactionCount();
    }
}
