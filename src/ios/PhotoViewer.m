/********* PhotoViewer.m Cordova Plugin Implementation *******/

#import <Cordova/CDV.h>
#import <Foundation/Foundation.h>
#import <UIKit/UIKit.h>

/*
 pasky edition.
 1/9/2016
 */

@interface PhotoViewer : CDVPlugin <UIScrollViewDelegate>
{
    UIImageView  *_imgv;
    UIScrollView *_scrollView;
    UIViewController *_vc;
}

@property (nonatomic, strong) UINavigationController *previewViewController;
@property (nonatomic, strong) NSMutableArray *documentURLs;

- (void)show:(CDVInvokedUrlCommand*)command;

@end

@implementation PhotoViewer

- (void)setupDocumentControllerWithURL:(NSURL *)url andTitle:(NSString *)title
{
    _vc                        = [[UIViewController alloc] init];
    _vc.view.backgroundColor   = [UIColor whiteColor];
    self.previewViewController = [[UINavigationController alloc] initWithRootViewController:_vc];

    [[NSNotificationCenter defaultCenter] addObserver:self selector:@selector(orientationChanged) name:UIDeviceOrientationDidChangeNotification object:nil];
}

- (UIView *)viewForZoomingInScrollView:(UIScrollView *)scrollView {
    return _imgv;
}

-(void)scrollViewDidZoom:(UIScrollView *)scrollView {
    _imgv.frame = [self centeredFrameForScrollView:_scrollView andUIView:_imgv];
}

- (CGRect)centeredFrameForScrollView:(UIScrollView *)scroll andUIView:(UIView *)rView {
    CGSize boundsSize    = scroll.bounds.size;
    CGRect frameToCenter = rView.frame;

    if (frameToCenter.size.width < boundsSize.width) {
        frameToCenter.origin.x = (boundsSize.width - frameToCenter.size.width) / 2;
    } else {
        frameToCenter.origin.x = 0;
    }

    if (frameToCenter.size.height < boundsSize.height) {
        frameToCenter.origin.y = (boundsSize.height - frameToCenter.size.height) / 2;
    } else {
        frameToCenter.origin.y = 0;
    }

    return frameToCenter;
}

- (void) updateZoom {
    float zoomScale = MIN(_scrollView.bounds.size.width / _imgv.image.size.width, _scrollView.bounds.size.height / _imgv.image.size.height);
    if (zoomScale > 1) {
        _scrollView.minimumZoomScale = 1;
    }
    _scrollView.minimumZoomScale = zoomScale;
    _scrollView.zoomScale = zoomScale;
}

- (void) orientationChanged {
    _imgv.frame = [self centeredFrameForScrollView:_scrollView andUIView:_imgv];
    [self updateZoom];
}

- (void) closeMe {
    [[NSNotificationCenter defaultCenter] removeObserver:self name:UIDeviceOrientationDidChangeNotification object:nil];
    [self.viewController dismissViewControllerAnimated:YES completion:nil];
}

- (void)show:(CDVInvokedUrlCommand*)command
{
    UIActivityIndicatorView *activityIndicator = [[UIActivityIndicatorView alloc] initWithFrame:self.viewController.view.frame];
    [activityIndicator setActivityIndicatorViewStyle:UIActivityIndicatorViewStyleWhiteLarge];
    [activityIndicator.layer setBackgroundColor:[[UIColor colorWithWhite:0.0 alpha:0.30] CGColor]];
    CGPoint center = self.viewController.view.center;
    activityIndicator.center = center;
    [self.viewController.view addSubview:activityIndicator];
    
    [activityIndicator startAnimating];

    CDVPluginResult* pluginResult = nil;
    NSString* url = [command.arguments objectAtIndex:0];
    NSString* title = [command.arguments objectAtIndex:1];

    if (url != nil && [url length] > 0) {
        [self.commandDelegate runInBackground:^{
            self.documentURLs = [NSMutableArray array];

            NSURL *URL = [self localFileURLForImage:url];

            if (URL) {
                [self.documentURLs addObject:URL];
                [self setupDocumentControllerWithURL:URL andTitle:title];
                double delayInSeconds = 0.1;
                dispatch_time_t popTime = dispatch_time(DISPATCH_TIME_NOW, delayInSeconds * NSEC_PER_SEC);
                dispatch_after(popTime, dispatch_get_main_queue(), ^(void){
                    [activityIndicator stopAnimating];

                    _imgv = [[UIImageView alloc] initWithImage:
                             [UIImage imageWithData:
                              [NSData dataWithContentsOfURL:URL]]];

                    CGRect svFrame = _vc.view.frame;
                    svFrame.origin.y    += 65;
                    svFrame.size.height -= 65;

                    // Remove top padding for iPhone X 
                    if (@available(iOS 11.0, *)) {
                        UIWindow *window = UIApplication.sharedApplication.keyWindow;
                        CGFloat topPadding = window.safeAreaInsets.top;
                        svFrame.origin.y += topPadding;
                        svFrame.size.height -= topPadding;
                    }

                    _scrollView = [[UIScrollView alloc] initWithFrame:svFrame];
                    _scrollView.contentSize = svFrame.size;
                    _scrollView.showsVerticalScrollIndicator = YES;
                    _scrollView.showsHorizontalScrollIndicator = YES;
                    _scrollView.scrollEnabled = YES;
                    _scrollView.maximumZoomScale = 5.0f;
                    _scrollView.delegate = self;
                    _scrollView.autoresizingMask = UIViewAutoresizingFlexibleLeftMargin | UIViewAutoresizingFlexibleRightMargin |
                    UIViewAutoresizingFlexibleTopMargin | UIViewAutoresizingFlexibleBottomMargin |
                    UIViewAutoresizingFlexibleWidth | UIViewAutoresizingFlexibleHeight;

                    [_scrollView addSubview:_imgv];

                    [self.previewViewController.view addSubview:_scrollView];

                    UIBarButtonItem *closeButton = [[UIBarButtonItem alloc] initWithBarButtonSystemItem:UIBarButtonSystemItemDone target:self action:@selector(closeMe)];
                    self.previewViewController.navigationItem.leftBarButtonItem = closeButton;
                    _vc.navigationItem.leftBarButtonItem = closeButton;

                    [self updateZoom];

                    [self.viewController presentViewController:self.previewViewController animated:YES completion:nil];
                });
            }
        }];
        pluginResult = [CDVPluginResult resultWithStatus:CDVCommandStatus_OK];
    } else {
        pluginResult = [CDVPluginResult resultWithStatus:CDVCommandStatus_ERROR];
    }

    [activityIndicator stopAnimating];
    [activityIndicator removeFromSuperview];

    [self.commandDelegate sendPluginResult:pluginResult callbackId:command.callbackId];
}

- (NSURL *)localFileURLForImage:(NSString *)image
{
    // save this image to a temp folder
    NSURL *tmpDirURL = [NSURL fileURLWithPath:NSTemporaryDirectory() isDirectory:YES];
    NSString *filename = [[NSUUID UUID] UUIDString];

    NSString* webStringURL = [image stringByAddingPercentEscapesUsingEncoding:NSUTF8StringEncoding];
    NSURL* fileURL = [NSURL URLWithString:webStringURL];
    
    if ([fileURL isFileReferenceURL]) {
        return fileURL;
    }

    NSData *data = [NSData dataWithContentsOfURL:fileURL];

    if( data ) {
        fileURL = [[tmpDirURL URLByAppendingPathComponent:filename] URLByAppendingPathExtension:[self contentTypeForImageData:data]];

        [[NSFileManager defaultManager] createFileAtPath:[fileURL path] contents:data attributes:nil];

        return fileURL;
    } else {
        return nil;
    }
}

- (NSString *)contentTypeForImageData:(NSData *)data {
    uint8_t c;
    [data getBytes:&c length:1];

    switch (c) {
        case 0xFF:
            return @"jpeg";
        case 0x89:
            return @"png";
        case 0x47:
            return @"gif";
        case 0x42:
            return @"bmp";
        case 0x49:
        case 0x4D:
            return @"tiff";
    }
    return nil;
}

@end