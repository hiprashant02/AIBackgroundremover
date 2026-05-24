plugins {
    id("com.android.asset-pack")
}

assetPack {
    packName.set("birefnet-model")
    dynamicDelivery {
        deliveryType.set("install-time")
    }
}
