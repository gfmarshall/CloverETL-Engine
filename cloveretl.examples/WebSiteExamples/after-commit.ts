<?xml version="1.0" encoding="UTF-8"?>
<!DOCTYPE TestScenario SYSTEM "testscenario.dtd">

<TestScenario ident="WebSiteExamples" description="WebSiteExamples distributed with Designer" useJMX="true">
	<GlobalRegEx ident="error" expression="^ERROR" caseSensitive="false" occurences="0" />
	
	<FunctionalTest ident="_Introduction" graphFile="graph/_Introduction.grf" />
	
	<FunctionalTest ident="CreditCardFraudDetection" graphFile="graph/CreditCardFraudDetection.grf" />
	
	<FunctionalTest ident="EmailValidation" graphFile="graph/EmailValidation.grf">
		<FlatFile outputFile="data-out/emails_domain_accept.txt" supposedFile="supposed-out/emails_domain_accept.txt"/>
		<FlatFile outputFile="data-out/emails_domain_reject.txt" supposedFile="supposed-out/emails_domain_reject.txt"/>
		<FlatFile outputFile="data-out/emails_smtp_accept.txt" supposedFile="supposed-out/emails_smtp_accept.txt"/>
		<FlatFile outputFile="data-out/emails_syntax_accept.txt" supposedFile="supposed-out/emails_syntax_accept.txt"/>
		<FlatFile outputFile="data-out/emails_syntax_reject.txt" supposedFile="supposed-out/emails_syntax_reject.txt"/>
	</FunctionalTest>
	
	<FunctionalTest ident="OrdersLookup" graphFile="graph/OrdersLookup.grf">
		<FlatFile outputFile="data-out/orders_late.txt" supposedFile="supposed-out/orders_late.txt"/>
	</FunctionalTest>
	
	<FunctionalTest ident="DataQualityFirewall" graphFile="jobflow/DataQualityFirewall.jbf" excludedEtlEnvironment="engine">
		<RegEx expression="Moved &quot;.*employees\.csv&quot; to &quot;.*data-out\/incoming_processed\/.*\/employees\.csv&quot;" occurences="1"/>
	</FunctionalTest>
	
	<FunctionalTest ident="ExecuteExternalUtility" graphFile="jobflow/ExecuteExternalUtility.jbf" excludedEtlEnvironment="engine" />
	
	<FunctionalTest ident="FileOperations" graphFile="jobflow/FileOperations.jbf" excludedEtlEnvironment="engine">
		<FlatFile outputFile="data-out/selected_customers.dat" supposedFile="supposed-out/selected_customers.dat"/>
	</FunctionalTest>
	
	<FunctionalTest ident="SalesforceWebService" graphFile="jobflow/SalesforceWebService.jbf" excludedEtlEnvironment="engine">
		<Property name="FORCE.COM_PASSWORD" value="frA8AS4U" />
		<Property name="FORCE.COM_SECURITY_TOKEN" value="aTSqcAM7QJa9dXZWPF5vjxxi" />
		<Property name="FORCE.COM_USER" value="support@javlin.eu" />
	</FunctionalTest>

</TestScenario>