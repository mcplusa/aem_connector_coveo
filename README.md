# Coveo AEM

This repository provides an integration of Coveo into AEM.

## Getting Started

### Preflight

#### Account Requirements:

 1. Access to Miscadmin (AEM Tools) (`http://<host>:<port>/miscadmin`):

  - The account should have access to install/modify a Replication Agent (`http://<host>:<port>/miscadmin#/etc/replication/agents.author`)

2. Access to AEM Web Console (`http://<host>:<port>/system/console/configMgr`)
The account should have access to:

  - Enable Apache Sling Login Admin Whitelist

  - Setup Externalizer - Day CQ Link Externalizer (`http://<host>:<port>/system/console/configMgr/com.day.cq.commons.impl.ExternalizerImpl`)

### Create a Push API Source

[Create a Push Source](https://docs.coveo.com/en/94/cloud-v2-developers/creating-a-push-source) and include the creation of a API Key, values that are needed are the following:

 - Organization ID
 - Source ID
 - Access Token _(API Token)_
 - Environment (production, hipaa, etc.)

### Build

To build the project, first is needed to build the [coveo-sdk-osgi](#repo) client.
Then, build the project using the following command:

```
mvn clean install
```

### Install Required Dependencies

Dependencies are required to be installed in AEM, the [complete](/complete) module aggregates all dependencies into a single content-package.

```
cd ./complete
mvn wcmio-content-package:install
```
_In case your AEM instance is not `http://localhost:4502/crx/packmgr/service`, you can set a custom service URL passing the parameter `serviceURL`, eg: `mvn wcmio-content-package:install -Dvault.serviceURL=http://my-custom-aem-domain.com/crx/packmgr/service`. 
For more information about available [parameters](https://wcm.io/tooling/maven/plugins/wcmio-content-package-maven-plugin/install-mojo.html)._

### Install Replication Agent

The [integration](/integration) module provides a Replication Agent that can be used to index AEM Pages / DAM Assets on activation.

```
cd ./integration
mvn sling:install -Dsling.url=http://localhost:4502
```

### Setup Replication Agent

After a successful installation, visit the [System Configuration](http://localhost:4502/system/console/configMgr) and setup a `Coveo Provider`, you will have the Agent Id after creating a Replication Agent.

The next step is to [setup a Replication Agent](http://localhost:4502/miscadmin#/etc/replication/agents.author) in `/etc/replication/agents.author` (The name of the created Agent will be the Agent Id needed in the the `Coveo Provider`. The recommended Title is `Coveo Index Agent` since it will generate the default agent id). To enable the Agent, open the Edit mode and check the Enabled box. You can also configure the desired log-level.

Now you are ready and can test the Connection. If everything works as expected, you should now see a successfull response.

### Setup Externalizer (Day CQ Link Externalizer)

By default, the absolute url of documents will use the [publish domain](https://docs.adobe.com/content/help/en/experience-manager-64/developing/platform/externalizer.html).

### Setup Login Admin Whitelist

The Login Admin Whitelist is used to retrieve ACL of documents and apply the permissions, to configure it visit the [System Configuration](http://localhost:4502/system/console/configMgr) and modify `Apache Sling Login Admin Whitelist`. Check `Bypass the whitelist` and add `coveo-aem-integration` in the `Whitelist regexp` input.


### Index custom fields

By default only basic fields are indexed. You can add additional fields using the Coveo Index Configuration in [System Configuration](http://localhost:4502/system/console/configMgr). Each entry contains a primary type (cq:Page or dam:Asset) and multiple index rules.

Please note that you will have to add all new fields to your [Fields mappings](https://docs.coveo.com/en/1833/cloud-v2-administrators/adding-and-managing-fields#add-a-field).

### Default Fields

Coveo only allows only allow lowercase letters, numbers, and underscores, the original field name will be automatically renamed. eg: `cq:template` in Coveo will be `cqtemplate`

#### cq:Page

| Field Name      | Field Name at Coveo | Usage              |
| --------------- | ------------------- | ------------------ |
| jcr:title       | title               | Page Title         |
| jcr:createdBy   | author              | Author             |
| cq:lastModified | date                | Last Modified Date |
| jcr:created     | createddate         | Created Date       |

#### dam:Asset

| Field Name       | Field Name at Coveo | Usage              |
| ---------------- | ------------------- | ------------------ |
| dc:title         | title               | Asset Title        |
| jcr:createdBy    | author              | Author             |
| jcr:lastModified | date                | Last Modified Date |
| jcr:created      | createddate         | Created Date       |

## Maintainer

* Francisco Pizarro / MC+A
* Contact sales@mcplusa.com

## License

[![License](http://img.shields.io/:license-mit-blue.svg?style=flat-square)](http://badges.mit-license.org)

- **[MIT license](http://opensource.org/licenses/mit-license.php)**
Contact MC+A for a commerial license or support
- Copyright 2020 © [Michael Cizmar & Associates Ltd.](https://www.mcplusa.com) All Rights Reserved.


