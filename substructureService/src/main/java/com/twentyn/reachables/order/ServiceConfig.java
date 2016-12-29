package com.twentyn.reachables.order;

import com.fasterxml.jackson.annotation.JsonProperty;

public class ServiceConfig {
  // The port on which to listen.
  @JsonProperty(value = "port", required = true)
  Integer port;

  // A path to a TSV file containing the reachables.  This will be loaded at startup time.
  @JsonProperty(value = "reachables_file", required = true)
  String reachablesFile;

  // The administrator's email address to use in order pages.
  @JsonProperty(value = "admin_email", required = true)
  String adminEmail;

  // A URL prefix to use for wiki page links.  Probably just https://wiki.20n.com/ or similar.
  @JsonProperty(value = "wiki_url_prefix", required = true)
  String wikiUrlPrefix;

  // A URL prefix to use for molecule images.  Probably https://wiki.20n.com/assets/img or similar.
  @JsonProperty(value = "image_url_prefix", required = true)
  String imageUrlPrefix;

  @JsonProperty(value = "aws_access_key_id")
  String accessKeyId;

  @JsonProperty(value = "aws_secret_access_key")
  String secretAccessKey;

  // The AWS region where the SNS topic to which to publish order messages lives.
  @JsonProperty(value = "aws_region")
  String region;

  // The SNS topic to which to send order messages.
  @JsonProperty(value = "aws_sns_topic")
  String snsTopic;

  public ServiceConfig() {
  }

  public ServiceConfig(Integer port, String reachablesFile, String adminEmail,
                       String wikiUrlPrefix, String imageUrlPrefix,
                       String accessKeyId, String secretAccessKey,
                       String region, String snsTopic) {
    this.port = port;
    this.reachablesFile = reachablesFile;
    this.adminEmail = adminEmail;
    this.wikiUrlPrefix = wikiUrlPrefix;
    this.imageUrlPrefix = imageUrlPrefix;
    this.accessKeyId = accessKeyId;
    this.secretAccessKey = secretAccessKey;
    this.region = region;
    this.snsTopic = snsTopic;
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

  public String getAdminEmail() {
    return adminEmail;
  }

  public void setAdminEmail(String adminEmail) {
    this.adminEmail = adminEmail;
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

  public String getAccessKeyId() {
    return accessKeyId;
  }

  public void setAccessKeyId(String accessKeyId) {
    this.accessKeyId = accessKeyId;
  }

  public String getSecretAccessKey() {
    return secretAccessKey;
  }

  public void setSecretAccessKey(String secretAccessKey) {
    this.secretAccessKey = secretAccessKey;
  }

  public String getRegion() {
    return region;
  }

  public void setRegion(String region) {
    this.region = region;
  }

  public String getSnsTopic() {
    return snsTopic;
  }

  public void setSnsTopic(String snsTopic) {
    this.snsTopic = snsTopic;
  }
}
