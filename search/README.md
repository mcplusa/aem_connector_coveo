# Coveo AEM Search

This bundle provides a Coveo Search Token page that can be used to implement the Coveo Search UI.

## Installation

You can build this bundle from source using Maven:

    mvn clean install

To install it into an [Apache Sling](http://sling.apache.org/) Environment (or AEM), use the sling:install command:

    mvn sling:install -Dsling.url=http://localhost:4502

For the initial installation, you should use the `complete` project which bundles all dependencies into a single artifact.

### Generate an Impersonation API Key

To generate the Search Token is necessary an API key with the privilege to impersonate users. See [Adding and Managing API Keys](https://docs.coveo.com/en/1718/cloud-v2-administrators/adding-and-managing-api-keys).

# Usage

## Setup
After a successful installation, visit the [System Configuration](http://localhost:4502/system/console/configMgr) and setup a `Coveo Search Configuration`.

 - Organization ID
 - Environment (production, hipaa, etc.)
 - Impersonation API Key _(from the [Generate an Impersonation API Key](#generate-an-impersonation-api-key) section)_
 - Users Identity Provider _Identity Provider used for User permissions_
 - Groups Identity Provider _Identity Provider used for Groups permissions_

## Create the Search Page

 - Go to the Website page at http://localhost:4502/siteadmin#/content.
 - Select New Page.
 - Specify the title of the page in the Title field.
 - Specify the name of the page in the Name field.
 - Select Coveo Search Token from the template list that appears.
 - Open the new page that you created by double-clicking it in the right pane. The new page opens in a web browser.
