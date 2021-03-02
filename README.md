# Event Source Using Apache Kafka With AWS Lambda Powered By Spring Cloud Stream/Function
__[Event-driven architecture](https://en.wikipedia.org/wiki/Event-driven_architecture)__ is a software architecture paradigm promoting the production, detection, consumption of, and reaction to events. It was created to help developers have a decoupled and responsive application. Because of this, it has been widely used in applications that have been broken down from monoliths to microservices. 

__[Spring Cloud Stream](https://docs.spring.io/spring-cloud-stream/docs/current/reference/html/#_quick_start)__ improves your productivity when working with Apache Kafka, RabbitMQ, Azure Event Hub, and more, providing three key abstractions to simplify your code. 

__[Spring Cloud Function](https://spring.io/projects/spring-cloud-function)__ enables you to write functions once and run them anywhere (AWS, Azure, etc.), while continuing to use all the familiar and comprehensive Spring APIs. You can chain multiple functions together to create new capabilities

The main goal of this __[repository](https://github.com/junbetterway/spring-cloud-func-aws-lambda-kafka-event-source-basic)__ is to introduce you on how we can create a serverless function using __AWS Lambda__ and creating a trigger via an event source such as __Apache Kafka__. If you want to know the basic for Spring Cloud Stream/Function with Kafka Binder then please visit my previous __[tutorial](https://github.com/junbetterway/spring-cloud-func-kafka-stream-binder-basic)__. 

__[AWS Lambda](https://aws.amazon.com/lambda/)__ now allows customers to build applications that can be triggered by messages in an Apache Kafka cluster hosted on any infrastructure - either __Self-managed Apache Kafka Cluster__ or __[AWS Managed Streaming for Kafka](https://aws.amazon.com/msk/)__. Customers can keep their existing Apache Kafka cluster as-is, and quickly and easily build Kafka consumer applications with Lambda without needing to worry about provisioning or managing servers. Please refer to the offical __[AWS Documentation](https://docs.aws.amazon.com/lambda/latest/dg/lambda-kafka.html)__ on how to use AWS Lambda with an Apache Kafka cluster for more details.

I prefer this __[blog](https://aws.amazon.com/blogs/compute/using-self-hosted-apache-kafka-as-an-event-source-for-aws-lambda/)__ since it has some examples on how to completely configure self-managed Apache Kafka as Event Source for AWS Lambda. 

## Getting Started
Our main application is pretty simple with traditional bean definition:

```
@SpringBootApplication
@Log4j2
public class SpringcloudfuncApplication {

	public static void main(String[] args) {
		SpringApplication.run(SpringcloudfuncApplication.class, args); 
	}

	@Bean
	public Function<Message<String>, String> greeter() {
		return (input) -> {
			log.info("Hello there {}", input.getPayload());
			return "Hello there " + input.getPayload();
		};
	}

}
```

It will just return a greeting based on the messages sent to a Kafka topic. Now, this is our __application.yml__ file:

```
spring:
  cloud:
    function:
      definition: greeter
    stream:
      kafka:
        default:
          consumer:
            startOffset: earliest
        binder:
          brokers: ${KAFKA_BROKERS:localhost:9092}
      bindings:
        greeter-in-0:
          destination: topic-names
        greeter-out-0:
          destination: topic-greetings
```

Given this, if you recall from our previous __[tutorial](https://github.com/junbetterway/spring-cloud-func-aws-lambda-basic)__ we do not need anymore to define an environment variable __SPRING_CLOUD_FUNCTION_DEFINITION__ during our creation of AWS Lambda function since it is already configured on the above yml file. However, we need to make sure that we define the value for our environment variable __KAFKA_BROKERS__ which is/are the values of __Private IPv4 DNS__ of the configured EC2 instances with Kafka cluster.

## Create Self-Managed Apache Kafka Cluster On AWS EC2 Instance
### A. Create The AWS Networking, Security Groups and EC2
1. Create a VPC with public and private subnetsC and a NAT Gateway (or NAT instance) that allows internet access on the routing table. See __[this](https://docs.aws.amazon.com/vpc/latest/userguide/VPC_Networking.html)__ for more details.
2. Create a Security Group for your Kafka cluster with the following inbound rules:

```
Custom TCP: port 2181 from all sources.
Custom TCP: port 9092 from all sources.
SSH: restricted to your own IP address or security group of your bastion server.
```

3. Create an EC2 instance and assign the private subnet you created from step 1. 
4. Assign an IAM role with __AmazonSSMManagedInstanceCore__ policy. 
5. Assign the security group you created from step 2.
6. Once the EC2 is up and running, connect to it either via SSH or via SSM.

### B. Install/Configure Apache Kafka/Zookeeper 
1. Install Java

```
sudo yum apt update
sudo amazon-linux-extras install java-openjdk11
```

2. Download and Extract Apache Kafka

```
wget http://www-us.apache.org/dist/kafka/2.7.0/kafka_2.13-2.7.0.tgz
tar xzf kafka_2.13-2.7.0.tgz
mv kafka_2.13-2.7.0 /usr/local/kafka
```

Our next steps will be to create systemd unit files for the Zookeeper and Kafka. This will help us to manage Kafka/Zookeeper to run as services using the systemctl command.

3. Setup Zookeeper Systemd Unit File. Run the command:

```
vim /etc/systemd/system/zookeeper.service
```

Paste below content:

```
[Unit]
Description=Apache Zookeeper server
Documentation=http://zookeeper.apache.org
Requires=network.target remote-fs.target
After=network.target remote-fs.target

[Service]
Type=simple
ExecStart=/usr/local/kafka/bin/zookeeper-server-start.sh /usr/local/kafka/config/zookeeper.properties
ExecStop=/usr/local/kafka/bin/zookeeper-server-stop.sh
Restart=on-abnormal

[Install]
WantedBy=multi-user.target
```

Save and exit

4. Setup Kafka Systemd Unit File. Run the command:

```
vim /etc/systemd/system/kafka.service
```

Paste below content:

*__Note:__ The environment fields below will depend on your setup. For my case, I use a free-tier EC2 instance type thus limited memory which explains the value for __KAFKA_HEAP_OPTS__. Also, the __JAVA_HOME__ value will depend on your JDK installation path. One can check this by running the command __update-alternatives --config java__.*

```
[Unit]
Description=Apache Kafka Server
Documentation=http://kafka.apache.org/documentation.html
Requires=zookeeper.service

[Service]
Type=simple
Environment="JAVA_HOME=/usr/lib/jvm/java-11-openjdk-11.0.9.11-0.amzn2.0.1.x86_64"
Environment="KAFKA_HEAP_OPTS=-Xmx256M -Xms256M"
ExecStart=/usr/local/kafka/bin/kafka-server-start.sh /usr/local/kafka/config/server.properties
ExecStop=/usr/local/kafka/bin/kafka-server-stop.sh

[Install]
WantedBy=multi-user.target
```

Save and exit

5. Reload the systemd daemon to apply new changes.

```
systemctl daemon-reload
```

6. Start Zookeeper Server

```
sudo systemctl start zookeeper
```

One can check the status if active by running below command:

```
sudo systemctl status zookeeper
```

7. Start Kafka Server

```
sudo systemctl start kafka
```

One can check the status if active by running below command:

```
sudo systemctl status kafka
```

8. Create a Topic in Kafka. Spring Boot will automatically create it for you but remember we will use it as a trigger for our AWS Lambda so might as well create it now and configure our Spring Boot application to subscribe to these topics. We will create two topics: __topic-names__ and __topic-greetings__

```
cd /usr/local/kafka
bin/kafka-topics.sh --create --zookeeper localhost:2181 --replication-factor 1 --partitions 1 --topic topic-names
bin/kafka-topics.sh --create --zookeeper localhost:2181 --replication-factor 1 --partitions 1 --topic topic-greetings
```

*__Note:__ The __replication-factor__ describes how many copies of data will be created and since we are running with a single instance keep this value 1. Set the __partitions__ option as the number of brokers you want your data to be split between. And since we are running with a single broker keep this value 1.*

To check all the topics under the Kafka cluster:

```
cd /usr/local/kafka
bin/kafka-topics.sh --list --zookeeper localhost:2181
```

9. Send Messages to Kafka - Producer

```
cd /usr/local/kafka
bin/kafka-console-producer.sh --broker-list localhost:9092 --topic topic-names
```

then type a few messages (hit enter) into the console to send to the server

10. Receive Messages from Kafka - Consumer

```
cd /usr/local/kafka
bin/kafka-console-consumer.sh --bootstrap-server localhost:9092 --topic topic-names --from-beginning
```

you should see the messages you created in step 9.

### C. Configure AWS Lambda With Apache Kafka As Trigger
One can visit my previous basic __[tutorial](https://github.com/junbetterway/spring-cloud-func-aws-lambda-basic)__ on how to build your Spring Cloud Function as shaded JAR then use it to create an AWS Lambda function.
1. Go to __[AWS Lambda Console](https://console.aws.amazon.com/lambda/home)__ then create a function by providing a unique name (__e.g.,__ MyGreeterFunc) and runtime environment (__e.g.,__ Java 11)
2. Once successfully created, let's setup the configuration by uploading the AWS deployable shaded JAR (__springcloudfunc-0.0.1-SNAPSHOT-aws.jar__) under the __Function Code__ section
3. Update the *Handler* field under __Runtime settings__ section and paste below default handler provided by Spring Cloud Function

```
org.springframework.cloud.function.adapter.aws.FunctionInvoker::handleRequest
```

4. Let's edit the __Environment variables__ and add a new entry __KAFKA_BROKERS__ which is explained under the *Getting Started* section above. The value of this is/are the __private IPv4 DNS__:__portNo__ of your EC2 instances as Apache Kafka broker (comma-delimited).

```
KAFKA_BROKERS=ip-10-0-1-199.ap-southeast-1.compute.internal:9092
```

5. Edit __Basic settings__ and increase the timeout from 15s to at least 1 minute. Feel free to add more memory (e.g., 1024).
6. Now go to __Permissions__ tab and click the role name to open the IAM console. Aside from the existing __AWSLambdaBasicExecutionRole__ policy, add a new inline policy with a name __SelfHostedKafkaPolicy__ with the following permissions:

```
{
    "Version": "2012-10-17",
    "Statement": [
        {
            "Effect": "Allow",
            "Action": [
                "ec2:CreateNetworkInterface",
                "ec2:DescribeNetworkInterfaces",
                "ec2:DescribeVpcs",
                "ec2:DeleteNetworkInterface",
                "ec2:DescribeSubnets",
                "ec2:DescribeSecurityGroups",
                "logs:CreateLogGroup",
                "logs:CreateLogStream",
                "logs:PutLogEvents"
            ],
            "Resource": "*"
        }
    ]
}
```

then save

7. Back in the Lambda function, go back to the __Configuration__ tab. In the *Designer* panel, choose __Add trigger__.
8. In the dropdown, select Apache Kafka:

```
For Bootstrap servers, add each EC2 instances (with Kafka broker) private IPv4 DNS addresses with port 9092 appended.
For Topic name, enter ‘topic-names’.
Enter your preferred batch size and starting position values (see this documentation for more information).
For VPC, select the VPC you created earlier in section A above
For VPC subnets, select the private subnet from section A above
For VPC security groups, select the Kafka security group from section A above
Enable trigger tickbox should be checked
Choose Add.
```

9. Wait for the new trigger to become enabled:

```
Apache Kafka: Endpoints: [ip-10-0-1-199.ap-southeast-1.compute.internal:9092] (Enabled)
```

## Testing The AWS Lambda Function
1. Send Messages to Kafka topic "topic-names" - Producer

```
cd /usr/local/kafka
bin/kafka-console-producer.sh --broker-list localhost:9092 --topic topic-names
```

then type a few messages (hit enter) into the console to send to the server (e.g., Jun King)

2. In the AWS Lambda function, select the __Monitoring__ tab then choose __View logs in CloudWatch__. In the latest log stream, you should see the original event with the following logs:

*__Note:__ Make sure to wait for a while as this is an event-driven flow and it take some time for the Spring Boot to initialize.*

```
2021-03-02 12:00:45.912  INFO 8 --- [           main] o.s.c.f.a.aws.CustomRuntimeEventLoop     : Located function greeter
2021-03-02 12:00:45.918  INFO 8 --- [           main] c.j.s.s.SpringcloudfuncApplication       : Hello there {
    "eventSource": "SelfManagedKafka",
    "bootstrapServers": "ip-10-0-1-199.ap-southeast-1.compute.internal:9092",
    "records": {
        "topic-names-0": [
            {
                "topic": "topic-names",
                "partition": 0,
                "offset": 13,
                "timestamp": 1614686444377,
                "timestampType": "CREATE_TIME",
                "value": "SnVuIEtpbmc="
            }
        ]
    }
}
```

One thing to notice from above response is that we received a payload with no "Jun King" as part of it. Well, one thing to notice is the following key-value pair under the topic-names-0:

```
"value": "SnVuIEtpbmc="
```

One can use this __[Online Base64Decoder](https://www.base64decode.org/)__ and enter the value "SnVuIEtpbmc=" then click Decode - one will get our expected String message "Jun King". I have posted a question on __[Stack Overflow](https://stackoverflow.com/questions/66435134/aws-lambda-with-apache-kafka-trigger-returns-problem-lambda-internal-error-pl)__ with an initial failure on the AWS Lambda trigger but has a follow-up question on the possible approach on this payload as we can simply decode this via our application code but that would mean we will have to traverse this and make our code AWS infra-aware. 

In any case, doing another producer message will trigger our newly created AWS Lambda function thus, we have successfully created an event-triggered AWS Lambda function running a Spring Boot application and listening to a self-managed Apache Kafka cluster.


## Powered By
Contact me at [junbetterway](mailto:jkpminon12@yahoo.com)

Happy coding!!!
