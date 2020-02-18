# Coveo AEM Integration

This bundle provides a Replication Agent that can be used to index AEM Pages / DAM Assets on activation. Furthermore some helper to perform a search are implemented.

## Installation

You can build this bundle from source using Maven:

    mvn clean install

To install it into an [Apache Sling](http://sling.apache.org/) Environment (or AEM), use the sling:install command:

    mvn sling:install -Dsling.url=http://localhost:4502

For the initial installation, you should use the `complete` project which bundles all dependencies into a single artifact.

# Usage

## Setup
After a successful installation, visit the [System Configuration](http://localhost:4502/system/console/configMgr) and setup a `Coveo Provider`.

NOTE: Currently no authentication is supported.

The next step is to [setup a Replication Agent](http://localhost:4502/miscadmin#/etc/replication/agents.author) in `/etc/replication/agents.author`. Although the edit mode provides numberous options, currently only `Enabled` and `Serialization Type` are used.

To enable the Agent, click the `Enabled` checkbox.

## Index custom fields

For each entry the path is index. In addion the following fields are indexed by default:

cq:Page
* jcr:title
* jcr:description
* cq:template
* cq:lastModified

dam:Asset
* dc:title
* dc:description
* jcr:lastModified

You can configure additional fields by creating a `Coveo Index Configuration` in [System Configuration](http://localhost:4502/system/console/configMgr).
