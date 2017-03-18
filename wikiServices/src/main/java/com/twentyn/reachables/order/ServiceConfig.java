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

  // A keyword that uniquely identifies the client who owns the wiki from which the order request originated.
  @JsonProperty(value = "client_keyword", required = true)
  String clientKeyword;

  // AWS access credentials, which can be found in the AWS IAM dashboard.
  @JsonProperty(value = "aws_access_key_id", required = true)
  String accessKeyId;

  @JsonProperty(value = "aws_secret_access_key", required = true)
  String secretAccessKey;

  // The AWS region where the SNS topic to which to publish order messages lives.
  @JsonProperty(value = "aws_region", required = true)
  String region;

  // The SNS topic to which to send order messages.
  @JsonProperty(value = "aws_sns_topic", required = true)
  String snsTopic;

  public ServiceConfig() {
  }

  public ServiceConfig(Integer port, String reachablesFile, String adminEmail,
                       String wikiUrlPrefix, String imageUrlPrefix, String clientKeyword,
                       String accessKeyId, String secretAccessKey,
                       String region, String snsTopic) {
    this.port = port;
    this.reachablesFile = reachablesFile;
    this.adminEmail = adminEmail;
    this.wikiUrlPrefix = wikiUrlPrefix;
    this.imageUrlPrefix = imageUrlPrefix;
    this.clientKeyword = clientKeyword;
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

  public String getClientKeyword() {
    return clientKeyword;
  }

  public void setClientKeyword(String clientKeyword) {
    this.clientKeyword = clientKeyword;
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
