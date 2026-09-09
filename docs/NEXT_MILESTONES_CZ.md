# Další milníky

## Compatibility spike

Cíl: Xcode 26.6 + DJI-SDK-iOS 4.16.2 se musí přeložit pro generic iOS device.

## Hardware link

Cíl: iPhone -> RC-N1 -> DJI Mini 2, registrace SDK a productConnected callback.

## RC override

Cíl: při aktivním Virtual Stick pohyb kteréhokoli fyzického kniplu nad deadzone okamžitě vypne Virtual Stick a stav přejde na HOLD/ABORT.

## Video + Vision

Cíl: dekódovat primaryVideoFeed na CVPixelBuffer a posílat snímky do VisionMultiWorkerTracker.

## Worker 1 + Worker 2

Cíl: ručně potvrdit identity obou pracovníků, vybrat PRIMARY/SECONDARY a provozovat Duo Follow nejprve pouze jako doporučení trajektorie.

## Supervised Flight

Cíl: povolit jen předem omezené manévry po trajectory review a explicitním potvrzení pilota.
