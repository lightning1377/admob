import Foundation
import Capacitor
import GoogleMobileAds

class BannerExecutor: NSObject, BannerViewDelegate {
    weak var plugin: AdMobPlugin?
    var bannerView: BannerView!
    var adPosition: String = ""
    var Margin: Int = 0

    func showBanner(_ call: CAPPluginCall, _ request: Request, _ adUnitID: String) {
        if let rootViewController = plugin?.getRootVC() {

            let adSize = call.getString("adSize") ?? "ADAPTIVE_BANNER"
            let adPosition = call.getString("position") ?? "BOTTOM_CENTER"
            let adMargin = call.getInt("margin") ?? 0

            var bannerSize: AdSize
            
            // 1. Calculate the available safe width
            let frame = rootViewController.view.frame.inset(by: rootViewController.view.safeAreaInsets)
            let viewWidth = frame.size.width
            
            // Helper: Get Adaptive Size
            let adaptiveSize = currentOrientationAnchoredAdaptiveBanner(width: viewWidth)

            // 2. Determine Size with Fallback Logic
            switch adSize {
            case "BANNER":
                bannerSize = AdSizeBanner
                break
            case "LARGE_BANNER":
                bannerSize = AdSizeLargeBanner
                break
            case "MEDIUM_RECTANGLE":
                bannerSize = AdSizeMediumRectangle
                break
            case "FULL_BANNER":
                // 468pt width. Fallback if screen is too narrow.
                bannerSize = viewWidth >= 468 ? AdSizeFullBanner : adaptiveSize
                break
            case "LEADERBOARD":
                // 728pt width (Tablets). Fallback if screen is too narrow.
                bannerSize = viewWidth >= 728 ? AdSizeLeaderboard : adaptiveSize
                break
            // This is deprecated, commented out to fallback to adaptive banner
            // case "SMART_BANNER":
            //     bannerSize = kGADAdSizeSmartBannerPortrait
            //     break
            default: // ADAPTIVE_BANNER
                bannerSize = adaptiveSize
                break
            }

            self.bannerView = BannerView(adSize: bannerSize)
            self.addBannerViewToView(self.bannerView, adPosition, adMargin)
            self.bannerView.translatesAutoresizingMaskIntoConstraints = false
            self.bannerView.adUnitID = adUnitID
            self.bannerView.rootViewController = plugin?.getRootVC()

            self.bannerView.load(request)
            self.bannerView.delegate = self

            call.resolve([:])
        }
    }

    func hideBanner(_ call: CAPPluginCall) {
        DispatchQueue.main.async {
            if let rootViewController = self.plugin?.getRootVC() {
                if let subView = rootViewController.view.viewWithTag(2743243288699) {
                    NSLog("AdMob: find subView for hideBanner")
                    subView.isHidden = true
                } else {
                    NSLog("AdMob: not find subView for resumeBanner for hideBanner")
                }
            }

            self.plugin?.notifyListeners(BannerAdPluginEvents.SizeChanged.rawValue, data: [
                "width": 0,
                "height": 0
            ])

            call.resolve([:])
        }
    }

    func resumeBanner(_ call: CAPPluginCall) {
        if let rootViewController = plugin?.getRootVC() {
            if let subView = rootViewController.view.viewWithTag(2743243288699) {
                NSLog("AdMob: find subView for resumeBanner")
                subView.isHidden = false

                self.plugin?.notifyListeners(BannerAdPluginEvents.SizeChanged.rawValue, data: [
                    "width": subView.frame.width,
                    "height": subView.frame.height
                ])

                call.resolve([:])

            } else {
                NSLog("AdMob: not find subView for resumeBanner")
                call.reject("AdMob: not find subView for resumeBanner")
            }
        }
    }

    func removeBanner(_ call: CAPPluginCall) {
        self.removeBannerViewToView()
        call.resolve([:])
    }

    private func addBannerViewToView(_ bannerView: BannerView, _ adPosition: String, _ Margin: Int) {
        removeBannerViewToView()
        bannerView.translatesAutoresizingMaskIntoConstraints = false
        bannerView.tag = 2743243288699 // rand
        self.Margin = Margin
        self.adPosition = adPosition
    }

    private func removeBannerViewToView() {
        if let rootViewController = plugin?.getRootVC() {
            if let subView = rootViewController.view.viewWithTag(2743243288699) {
                bannerView.delegate = nil
                NSLog("AdMob: find subView")
                subView.removeFromSuperview()
            }
        }
    }

    /// Tells the delegate an ad request loaded an ad.
    func bannerViewDidReceiveAd(_ bannerView: BannerView) {
        NSLog("bannerViewDidReceiveAd")
        if let rootViewController = plugin?.getRootVC() {
            rootViewController.view.addSubview(bannerView)
            
            // Enable AutoLayout
            bannerView.translatesAutoresizingMaskIntoConstraints = false
            
            // 3. Apply Constraints (Center & Safe Area)
            var constraints: [NSLayoutConstraint] = [
                // Always center horizontally
                bannerView.centerXAnchor.constraint(equalTo: rootViewController.view.centerXAnchor)
            ]
            
            if self.adPosition == "TOP_CENTER" {
                // Pin to Safe Area TOP + Margin
                constraints.append(
                    bannerView.topAnchor.constraint(
                        equalTo: rootViewController.view.safeAreaLayoutGuide.topAnchor,
                        constant: CGFloat(self.Margin)
                    )
                )
            } else {
                // Pin to Safe Area BOTTOM - Margin
                constraints.append(
                    bannerView.bottomAnchor.constraint(
                        equalTo: rootViewController.view.safeAreaLayoutGuide.bottomAnchor,
                        constant: CGFloat(-self.Margin)
                    )
                )
            }
            
            NSLayoutConstraint.activate(constraints)
            
            self.plugin?.notifyListeners(BannerAdPluginEvents.SizeChanged.rawValue, data: [
                "width": bannerView.frame.width,
                "height": bannerView.frame.height
            ])
            self.plugin?.notifyListeners(BannerAdPluginEvents.Loaded.rawValue, data: [
                "isCollapsible": bannerView.isCollapsible
            ])
        }
    }

    /// Tells the delegate an ad request failed.
    func bannerView(_ bannerView: BannerView,
                    didFailToReceiveAdWithError error: Error) {
        NSLog("bannerView:didFailToReceiveAdWithError: \(error.localizedDescription)")
        self.removeBannerViewToView()
        self.plugin?.notifyListeners(BannerAdPluginEvents.SizeChanged.rawValue, data: [
            "width": 0,
            "height": 0
        ])
        self.plugin?.notifyListeners(BannerAdPluginEvents.FailedToLoad.rawValue, data: [
            "code": 0,
            "message": error.localizedDescription
        ])
    }

    func bannerViewDidRecordImpression(_ bannerView: BannerView) {
        self.plugin?.notifyListeners(BannerAdPluginEvents.AdImpression.rawValue, data: [:])
    }

    /// Tells the delegate that a full-screen view will be presented in response
    /// to the user clicking on an ad.
    func bannerViewWillPresentScreen(_ bannerView: BannerView) {
        self.plugin?.notifyListeners(BannerAdPluginEvents.Opened.rawValue, data: [:])
    }

    /// Tells the delegate that the full-screen view will be dismissed.
    func bannerViewWillDismissScreen(_ bannerView: BannerView) {
        self.plugin?.notifyListeners(BannerAdPluginEvents.Closed.rawValue, data: [:])
    }
}