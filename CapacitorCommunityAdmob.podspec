require 'json'

package = JSON.parse(File.read(File.join(__dir__, 'package.json')))

Pod::Spec.new do |s|
  s.name = 'CapacitorCommunityAdmob'
  s.version = package['version']
  s.summary = package['description']
  s.license = package['license']
  s.homepage = package['repository']['url']
  s.author = package['author']
  s.source = { :git => package['repository']['url'], :tag => s.version.to_s }
  s.source_files = 'ios/Sources/**/*.{swift,h,m,c,cc,mm,cpp}'
  s.ios.deployment_target = '14.0'
  s.swift_version = '5.1'
  s.static_framework = true
  s.dependency 'Capacitor'
  s.dependency 'Google-Mobile-Ads-SDK', '12.7.0'
  s.dependency 'GoogleUserMessagingPlatform', '3.0.0'
end
