package gs.ad.gsadsexample.sub

enum class MyProductId (val id: String){
    Lifetime(ConsumableProductId.Lifetime.id),
    Weekly(SubscriptionProductId.Weekly.id),
    Yearly(SubscriptionProductId.Yearly.id)
}