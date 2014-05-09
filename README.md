# Anypoint Template: sfdc2sfdc-case-customobject-bidirectional-sync

+ [Use Case](#usecase)
+ [Run it!](#runit)
    * [Running on CloudHub](#runoncloudhub)
    * [Running on premise](#runonopremise)
        * [Properties to be configured](#propertiestobeconfigured)
+ [Customize It!](#customizeit)
    * [config.xml](#configxml)
    * [endpoints.xml](#endpointsxml)
    * [businessLogic.xml](#businesslogicxml)
    * [errorHandling.xml](#errorhandlingxml)


# Use Case <a name="usecase"/>

As a Salesforce admin I want to have my Cases from orgA syncronized with Custom objects from Salesforce orgB.

This Template should serve as a foundation for setting an online bi-directional sync of Cases and Custom objects between two SalesForce instances, being able to specify filtering criterias. 

The integration main behaviour is polling for changes (new Cases/Custom objects or modified ones) that have occured in any of the Salesforces instances during a certain defined period of time. For those Contacts that both have not been updated yet the integration triggers an upsert (update or create depending the case) taking the last modification as the one that should be applied.

Additionally, there are two subflows which are syncing Accounts and Contacts if the Case in orgA or Case__c custom object in orgB in manner of one time integration.
If the Account or Contact field in Case object is specified and the Account or Contact does not exist in other org, it will be created for first time.
The matching criteria for Account is field 'Account Name'. And matching criteria for Contact is field 'Email'.

Requirements have been set not only to be used as examples, but also to stablish starting point to adapt the integration to any given requirements.


# Run it! <a name="runit"/>

In order to have your application up and running you just need to complete two simple steps:

 1. [Configure the application properties](#propertiestobeconfigured)
 2. Run it! ([on premise](#runonopremise) or [in Cloudhub](#runoncloudhub))


## Properties to be configured<a name="propertiestobeconfigured"/>

In order to use this Anypoint Template you need to configure a couple of properties (credentials, configurations, etc.) either in properties file, or in CloudHub as Environment Variables. 

### Detailed list of needed properties, (with examples):

#### Application configuration
+ polling.frequency `10000`  
This are the miliseconds that will run between two different checks for updates in Salesforce

+ watermark.default.expression `2014-02-25T11:00:00.000Z`  
This property is an important one, as it configures what should be the start point of the synchronization. If the use case includes synchronizing every contact created from the begining of the times, you should use a date previous to any contact creation (perhaphs `1900-01-01T08:00:00.000Z` is a good choice). If you want to synchronize the contacts created from now on, then you should use a default value according to that requirement (for example, today is Febraury 25th of 2014 and it's eleven o'clock, then I would take the following value `2014-02-25T11:00:00.000Z`).

#### SalesForce Connector configuration for company A
+ sfdc.a.username `bob.dylan@orga`
+ sfdc.a.password `DylanPassword123`
+ sfdc.a.securityToken `avsfwCUl7apQs56Xq2AKi3X`
+ sfdc.a.url `https://login.salesforce.com/services/Soap/u/26.0`
+ sfdc.a.integration.user.id `005d0000000yYC7AAM`
 

#### SalesForce Connector configuration for company B
+ sfdc.b.username `joan.baez@orgb`
+ sfdc.b.password `JoanBaez456`
+ sfdc.b.securityToken `ces56arl7apQs56XTddf34X`
+ sfdc.b.url `https://login.salesforce.com/services/Soap/u/26.0`
+ sfdc.b.integration.user.id `005i0000002qZ75AAE`


### Some points to consider about configuration properties

Polling Frecuency is expressed in miliseconds (different time units can be used) and the Watermark Default Expression defines the date to be used to query the first time the integration runs. [More details about polling and watermarking.](http://www.mulesoft.org/documentation/display/current/Poll+Reference)

The date format accepted in SFDC Query Language is either YYYY-MM-DDThh:mm:ss+hh:mm or you can use Constants (Like YESTERDAY in the example). [More information about Dates in SFDC.](http://www.salesforce.com/us/developer/docs/officetoolkit/Content/sforce_api_calls_soql_select_dateformats.htm)

The query fields list must include both 'Email' and 'LastModifiedDate' fields, as those fields are embedded in the integration business logic

#### Structure of the Case object

To be able to match pair Case object in orgA with Case custom object in orgB we needed to extend Case object in orgA with following field. 

+ ExtId__c `Text(50) External ID` external id which is referecing ID of Case__c in orgB

Feel free to customize the name of this field, but keep in mind that you will need to update all occurences in flows for this particular field reference.


#### Structure of the Case Custom object

In this particular template we named the Custom object `Case`, that is the API name of the Custom object in orgB is `Case__c`, so all soql queries, datamapper conversions are issued to this particular object. Case__c custom object inherits all required fields from Case in orgA together with Description__c, Subject__c text fields and
Contact__c and Account__c lookup fields.

+ Account__c `Lookup(Account)`
+ CaseId__c `Text(50) External ID` external id which is referecing ID of Case in orgA
+ Contact__c `Lookup(Contact)`
+ Description__c `Text(50)`
+ Origin__c `Text(50) Required`
+ Priority__c `Text(50) Required`
+ Status__c `Text(50) Required`
+ Subject__c `Text(50)`

This structure may be customized but keep in mind that you will then need to 
+ update query `fetch case__c objects from B` in `endpoints.mflow` to fetch all extra fields
+ update datamapper component `transform Case to Case__c`
+ update datamapper component `transform Case__c to Case`


## Running on CloudHub <a name="runoncloudhub"/>

In order to [create your application on CloudHub](http://www.mulesoft.org/documentation/display/current/Hello+World+on+CloudHub) you should to go to Deployment > Advanced to set all environment variables detailed in **Properties to be configured** as well as the **mule.env**. 

Once your app is all set and started, supposing you choose as domain name `sfdc2sfdc-bidirectional-contact-sync` to trigger the use case you just need to hit `http://sfdc2sfdc-bidirectional-contact-sync.cloudhub.io/synccontacts` and report will be sent to the emails configured.

## Running on premise <a name="runonopremise"/>
Complete all properties in one of the property files, for example in [mule.prod.properties] (../blob/master/src/main/resources/mule.prod.properties) and run your app with the corresponding environment variable to use it. To follow the example, this will be `mule.env=prod`.

After this,  the integration will fetch the updates occured since the date configured in the watermark.default.expression property.

# Customize It!<a name="customizeit"/>
This brief guide intends to give a high level idea of how this Template is built and how you can change it according to your needs.
As mule applications are based on XML files, this page will be organised by describing all the XML that conform the Template.
Of course more files will be found such as Test Classes and [Mule Application Files](http://www.mulesoft.org/documentation/display/current/Application+Format), but to keep it simple we will focus on the XMLs.

Here is a list of the main XML files you'll find in this application:

* [config.xml](#configxml)
* [endpoints.xml](#endpointsxml)
* [businessLogic.xml](#businesslogicxml)
* [errorHandling.xml](#errorhandlingxml)


## config.xml<a name="configxml"/>
This file holds the configuration for Connectors and [Properties Place Holders](http://www.mulesoft.org/documentation/display/current/Configuring+Properties) are set in this file. 
**Even you can change the configuration here, all parameters that can be modified here are in properties file, and this is the recommended place to do it so.** 
Of course if you want to do core changes to the logic you will probably need to modify this file.

In the visual editor they can be found on the *Global Element* tab.


## endpoints.xml<a name="endpointsxml"/>
This is the file where you will found the inbound and outbound sides of your integration app.
It is intented to define the application API.
...

## businessLogic.xml<a name="businesslogicxml"/>
This file holds the functional aspect of the template , directed by one flow responsible of conducting the business logic.
...


## errorHandling.xml<a name="errorhandlingxml"/>
This is the right place to handle how your integration will react depending on the different exceptions. 
This file holds a [Choice Exception Strategy](http://www.mulesoft.org/documentation/display/current/Choice+Exception+Strategy) that is referenced by the main flow in the business logic.
...
