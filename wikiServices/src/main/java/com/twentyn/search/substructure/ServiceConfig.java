package com.twentyn.search.substructure;

import com.fasterxml.jackson.annotation.JsonProperty;

/**
 * A simple container class for writing static configuration parameters.  Easier than having to update the /etc/init.d
 * script every time you want to make a config change.
 */
public class ServiceConfig {
  // The port on which to listen.
  @JsonProperty(value = "port", required = true)
  Integer port;

  // A path to a TSV file containing the reachables.  This will be loaded at startup time.
  @JsonProperty(value = "reachables_file", required = true)
  String reachablesFile;

  // A path to a Chemaxon license file to load at startup.  A valid license is required for substructure search.
  @JsonProperty(value = "license_file", required = true)
  String licenseFile;

  // A URL prefix to use for wiki page links.  Probably just https://wiki.20n.com/ or similar.
  @JsonProperty(value = "wiki_url_prefix", required = true)
  String wikiUrlPrefix;

  // A URL prefix to use for molecule images.  Probably https://wiki.20n.com/assets/img or similar.
  @JsonProperty(value = "image_url_prefix", required = true)
  String imageUrlPrefix;

  public ServiceConfig() {
  }

  public ServiceConfig(Integer port, String reachablesFile, String licenseFile,
                       String wikiUrlPrefix, String imageUrlPrefix) {
    this.port = port;
    this.reachablesFile = reachablesFile;
    this.licenseFile = licenseFile;
    this.wikiUrlPrefix = wikiUrlPrefix;
    this.imageUrlPrefix = imageUrlPrefix;
  }

  public Integer getPort() {
    return port;
  }

  public void setPort(Integer port) {
    this.port = port;
  }

  public String getReachablesFile() {
    return reachablesFile;
  }

  public void setReachablesFile(String reachablesFile) {
    this.reachablesFile = reachablesFile;
  }

  public String getLicenseFile() {
    return licenseFile;
  }

  public void setLicenseFile(String licenseFile) {
    this.licenseFile = licenseFile;
  }

  public String getWikiUrlPrefix() {
    return wikiUrlPrefix;
  }

  public void setWikiUrlPrefix(String wikiUrlPrefix) {
    this.wikiUrlPrefix = wikiUrlPrefix;
  }

  public String getImageUrlPrefix() {
    return imageUrlPrefix;
  }

  public void setImageUrlPrefix(String imageUrlPrefix) {
    this.imageUrlPrefix = imageUrlPrefix;
  }
}
