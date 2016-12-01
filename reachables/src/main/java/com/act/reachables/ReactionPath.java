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

    //@JsonProperty("path")
    private List<Cascade.NodeInformation> path;

    @JsonProperty("_id")
    private String id;

    private Long target;
    private Long rank;
    private Integer degree;
    private Integer reactionSum;
    private Boolean isMostNative = false;

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

    //@JsonCreator
    public ReactionPath() {}

    //@JsonCreator
    public ReactionPath(/*@JsonProperty("_id")*/ String id, /*@JsonProperty("path")*/ List<Cascade.NodeInformation> path) {
        this.id = id;
        this.path = path;
        this.target = Long.parseLong(this.id.split("w")[0]);
        this.rank = Long.parseLong(this.id.split("w")[1]);
        this.degree = this.getReactionCount(this.path.get(1));
        this.reactionSum = this.path.stream().map(x -> getReactionCount(x)).collect(Collectors.summingInt(Integer::intValue));
    }

    //@JsonCreator
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
