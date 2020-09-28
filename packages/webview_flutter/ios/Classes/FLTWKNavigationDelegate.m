// Copyright 2019 The Chromium Authors. All rights reserved.
// Use of this source code is governed by a BSD-style license that can be
// found in the LICENSE file.

#import "FLTWKNavigationDelegate.h"

@implementation FLTWKNavigationDelegate {
  FlutterMethodChannel* _methodChannel;
}

- (instancetype)initWithChannel:(FlutterMethodChannel*)channel {
  self = [super init];
  if (self) {
    _methodChannel = channel;
  }
  return self;
}

#pragma mark - WKNavigationDelegate conformance

- (void)webView:(WKWebView*)webView didStartProvisionalNavigation:(WKNavigation*)navigation {
  [_methodChannel invokeMethod:@"onPageStarted" arguments:@{@"url" : webView.URL.absoluteString}];
}

- (void)webView:(WKWebView*)webView
    decidePolicyForNavigationAction:(WKNavigationAction*)navigationAction
                    decisionHandler:(void (^)(WKNavigationActionPolicy))decisionHandler {
  // to make wxpay returns app after paying
  NSURL *url = navigationAction.request.URL;
  if (url != nil && url.absoluteString != nil && [url.absoluteString hasPrefix:@"https://wx.tenpay.com/cgi-bin/mmpayweb-bin/checkmweb"]) {
    if ([url.absoluteString rangeOfString:@"redirect_url="].location == NSNotFound) {
      NSString *refererSchema =  [NSString stringWithFormat:@"%@://", WX_H5_PAY_SCHEMA];
      NSString *newUrl = [NSString stringWithFormat:@"%@&redirect_url=%@", url.absoluteString, refererSchema];
      NSMutableURLRequest* request = [NSMutableURLRequest requestWithURL:[NSURL URLWithString:newUrl] cachePolicy:NSURLRequestUseProtocolCachePolicy timeoutInterval:60.0];
      [request setValue:refererSchema forHTTPHeaderField:@"Referer"];
      [webView loadRequest:request];
      decisionHandler(WKNavigationActionPolicyCancel);
      return;
    }
  }
    
  int allowFlag = WKNavigationActionPolicyAllow + 2;
  if (!self.hasDartNavigationDelegate) {
    decisionHandler(allowFlag);
    return;
  }
  NSDictionary* arguments = @{
    @"url" : navigationAction.request.URL.absoluteString,
    @"isForMainFrame" : @(navigationAction.targetFrame.isMainFrame)
  };
  [_methodChannel invokeMethod:@"navigationRequest"
                     arguments:arguments
                        result:^(id _Nullable result) {
                          if ([result isKindOfClass:[FlutterError class]]) {
                            NSLog(@"navigationRequest has unexpectedly completed with an error, "
                                  @"allowing navigation.");
                            decisionHandler(allowFlag);
                            return;
                          }
                          if (result == FlutterMethodNotImplemented) {
                            NSLog(@"navigationRequest was unexepectedly not implemented: %@, "
                                  @"allowing navigation.",
                                  result);
                            decisionHandler(allowFlag);
                            return;
                          }
                          if (![result isKindOfClass:[NSNumber class]]) {
                            NSLog(@"navigationRequest unexpectedly returned a non boolean value: "
                                  @"%@, allowing navigation.",
                                  result);
                            decisionHandler(allowFlag);
                            return;
                          }
                          NSNumber* typedResult = result;
                          decisionHandler([typedResult boolValue] ? allowFlag
                                                                  : WKNavigationActionPolicyCancel);
                        }];
}

- (void)webView:(WKWebView*)webView didFinishNavigation:(WKNavigation*)navigation {
  [_methodChannel invokeMethod:@"onPageFinished" arguments:@{@"url" : webView.URL.absoluteString}];
}
@end
