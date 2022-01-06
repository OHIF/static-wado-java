import * as cdk from 'aws-cdk-lib';
import { aws_s3 as s3 } from 'aws-cdk-lib';

export class Mkdicomwebs3Stack extends cdk.Stack {
  constructor(scope: cdk.App, id: string, props?: cdk.StackProps) {
    super(scope, id, props);

    const wadoBucket = new s3.Bucket(this, 'static-wado', {
      versioned: false,
      removalPolicy: cdk.RemovalPolicy.DESTROY,
      autoDeleteObjects: true,
    });

    const ohifBucket = new s3.Bucket(this, 'ohif', {
      versioned: false,
      removalPolicy: cdk.RemovalPolicy.DESTROY,
      websiteIndexDocument: "index.html",
      websiteErrorDocument: "index.html",
      publicReadAccess: true,
      autoDeleteObjects: true,
    });
    

    new cdk.CfnOutput(this, "WadoBucket", { value: wadoBucket.bucketName });
    new cdk.CfnOutput(this, "OHIFBucket", { value: ohifBucket.bucketName });

  }
}