import * as cdk from 'aws-cdk-lib';
import {
  aws_s3 as s3,
  aws_s3_deployment as s3deploy,
  aws_cloudfront as cloudfront,
  aws_cloudfront_origins as origins,
} from 'aws-cdk-lib';

export class Mkdicomwebs3Stack extends cdk.Stack {
  constructor(scope: cdk.App, id: string, props?: cdk.StackProps) {
    super(scope, id, props);

    const wadoBucket = new s3.Bucket(this, 'DICOMweb', {
      versioned: false,
      websiteIndexDocument: "index.html",
      removalPolicy: cdk.RemovalPolicy.DESTROY,
      publicReadAccess: true,
      autoDeleteObjects: true,
      cors: [
        {
          allowedOrigins: ['*'],
          allowedMethods: [s3.HttpMethods.GET],
        }
      ],
    });

    // This is just test data to show it works or not
    new s3deploy.BucketDeployment(this, 'DeployDummyData', {
      sources: [s3deploy.Source.asset('./dummyDicomWeb')],
      destinationBucket: wadoBucket,
    });


    const clientBucket = new s3.Bucket(this, 'Client', {
      versioned: false,
      removalPolicy: cdk.RemovalPolicy.DESTROY,
      websiteIndexDocument: "index.html",
      websiteErrorDocument: "index.html",
      publicReadAccess: true,
      autoDeleteObjects: true,
    });

    new s3deploy.BucketDeployment(this, 'DeployWebsite', {
      sources: [s3deploy.Source.asset('/src/Viewers/platform/viewer/dist')],
      destinationBucket: clientBucket,
    });

    const dist = new cloudfront.Distribution(this, 'myDist', {
      defaultBehavior: { origin: new origins.S3Origin(clientBucket) },
      additionalBehaviors: {
        '/dicomweb/*': {
          origin: new origins.S3Origin(wadoBucket),
          pathPattern: '/dicomweb/*',
        },
      },
    });

    new cdk.CfnOutput(this, "WadoBucket", { value: wadoBucket.bucketName });
    new cdk.CfnOutput(this, "OHIFBucket", { value: clientBucket.bucketName });
    new cdk.CfnOutput(this, 'ClientURL', {
      value: clientBucket.bucketWebsiteUrl,
      description: "'Client Website URL',"
    });
    new cdk.CfnOutput(this, 'DICOMwebURL', {
      value: wadoBucket.bucketWebsiteUrl,
      description: "'DICOMweb URL',"
    });
    new cdk.CfnOutput(this, 'WebURL', {
      value: dist.distributionDomainName,
      description: "'Web URL',"
    });

  }
}