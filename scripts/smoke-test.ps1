param(
    [string]$BaseUrl = 'http://localhost:8080',
    [string]$AdminUsername = $env:ADMIN_USERNAME,
    [string]$AdminPassword = $env:ADMIN_PASSWORD
)
$ErrorActionPreference = 'Stop'
if (-not $AdminUsername -or -not $AdminPassword) {
    throw 'Set ADMIN_USERNAME and ADMIN_PASSWORD or pass parameters before running this smoke test.'
}
function Invoke-Api($Method, $Path, $Body, $Token, $Key) {
    $parameters = @{ Method = $Method; Uri = "$BaseUrl$Path"; Headers = @{} }
    if ($null -ne $Body) {
        $parameters.ContentType = 'application/json; charset=utf-8'
        $parameters.Body = [System.Text.Encoding]::UTF8.GetBytes(($Body | ConvertTo-Json -Depth 10))
    }
    if ($Token) { $parameters.Headers.Authorization = "Bearer $Token" }
    if ($Key) { $parameters.Headers.'Idempotency-Key' = $Key }
    Invoke-RestMethod @parameters
}
function Assert-Stock($SkuId, $Available, $Reserved, $Token) {
    $stock = Invoke-Api GET "/api/v1/admin/inventory/$SkuId" $null $Token
    if ($stock.availableStock -ne $Available -or $stock.reservedStock -ne $Reserved) { throw 'Unexpected inventory balance' }
}
$suffix = [Guid]::NewGuid().ToString('N').Substring(0, 12)
$username = "smoke_$suffix"
$password = [Guid]::NewGuid().ToString('N')
$admin = Invoke-Api POST '/api/v1/admin/auth/login' @{ account = $AdminUsername; password = $AdminPassword }
$category = Invoke-Api POST '/api/v1/admin/categories' @{ name = "Smoke $suffix"; sortOrder = 0 } $admin.accessToken
$product = Invoke-Api POST '/api/v1/admin/products' @{
    categoryId = $category.id; name = 'Smoke keyboard'; description = 'Deployment verification'; brand = 'OrderHub'
} $admin.accessToken
$sku = Invoke-Api POST "/api/v1/admin/products/$($product.id)/skus" @{
    skuCode = "SMOKE-$suffix"; attributes = @{ color = 'Black' }; price = 49.90; status = 'ACTIVE'; initialStock = 20
} $admin.accessToken
$null = Invoke-Api PUT "/api/v1/admin/products/$($product.id)/publish" $null $admin.accessToken
$null = Invoke-Api POST '/api/v1/user/auth/register' @{ email = "$username@smoke.local"; username = $username; password = $password }
$user = Invoke-Api POST '/api/v1/user/auth/login' @{ account = $username; password = $password }
$address = Invoke-Api POST '/api/v1/user/addresses' @{
    receiverName = 'Smoke User'; phone = '13800138000'; province = 'Shanghai'; city = 'Shanghai'
    district = 'Pudong'; detailAddress = 'Test Road 1'; isDefault = $true
} $user.accessToken
$null = Invoke-Api GET "/api/v1/user/products/$($product.id)"
$null = Invoke-Api GET "/api/v1/user/products/$($product.id)/skus"
$null = Invoke-Api POST '/api/v1/user/cart/items' @{ skuId = $sku.id; quantity = 2 } $user.accessToken
$checkout = @{ addressId = $address.id; remark = 'Deployment test' }
$order = Invoke-Api POST '/api/v1/user/orders' $checkout $user.accessToken "complete-$suffix"
$retry = Invoke-Api POST '/api/v1/user/orders' $checkout $user.accessToken "complete-$suffix"
if ($order.order.id -ne $retry.order.id -or $order.order.payableAmount -ne 99.8) { throw 'Checkout amount or idempotency failed' }
Assert-Stock $sku.id 18 2 $admin.accessToken
$payment = Invoke-Api POST "/api/v1/user/orders/$($order.order.id)/payments" @{ method = 'MOCK' } $user.accessToken
$null = Invoke-Api POST "/api/v1/user/payments/$($payment.id)/mock-success" $null $user.accessToken
$null = Invoke-Api POST "/api/v1/user/payments/$($payment.id)/mock-success" $null $user.accessToken
$null = Invoke-Api POST "/api/v1/admin/orders/$($order.order.id)/process" $null $admin.accessToken
$shipment = Invoke-Api POST "/api/v1/admin/orders/$($order.order.id)/ship" @{ carrier = 'MOCK'; trackingNumber = "TRACK-$suffix" } $admin.accessToken
$null = Invoke-Api PUT "/api/v1/admin/shipments/$($shipment.id)/status" @{ status = 'DELIVERED' } $admin.accessToken
$complete = Invoke-Api POST "/api/v1/user/orders/$($order.order.id)/confirm-receipt" $null $user.accessToken
if ($complete.order.status -ne 'COMPLETED') { throw 'Completion failed' }
Assert-Stock $sku.id 18 0 $admin.accessToken

$null = Invoke-Api POST '/api/v1/user/cart/items' @{ skuId = $sku.id; quantity = 1 } $user.accessToken
$cancelOrder = Invoke-Api POST '/api/v1/user/orders' $checkout $user.accessToken "cancel-$suffix"
$null = Invoke-Api POST "/api/v1/user/orders/$($cancelOrder.order.id)/cancel" @{ reason = 'Smoke cancellation' } $user.accessToken
$null = Invoke-Api POST "/api/v1/user/orders/$($cancelOrder.order.id)/cancel" @{ reason = 'Smoke retry' } $user.accessToken
Assert-Stock $sku.id 18 0 $admin.accessToken

$null = Invoke-Api POST '/api/v1/user/cart/items' @{ skuId = $sku.id; quantity = 1 } $user.accessToken
$refundOrder = Invoke-Api POST '/api/v1/user/orders' $checkout $user.accessToken "refund-$suffix"
$payment = Invoke-Api POST "/api/v1/user/orders/$($refundOrder.order.id)/payments" @{ method = 'MOCK' } $user.accessToken
$null = Invoke-Api POST "/api/v1/user/payments/$($payment.id)/mock-success" $null $user.accessToken
$refund = Invoke-Api POST "/api/v1/user/orders/$($refundOrder.order.id)/refunds" @{ reason = 'Smoke refund' } $user.accessToken
$null = Invoke-Api POST "/api/v1/admin/refunds/$($refund.id)/approve" @{ reason = 'Approved for deployment test' } $admin.accessToken
$null = Invoke-Api POST "/api/v1/admin/refunds/$($refund.id)/complete" $null $admin.accessToken
Assert-Stock $sku.id 18 0 $admin.accessToken
$null = Invoke-Api GET '/api/v1/admin/dashboard/overview' $null $admin.accessToken
$history = Invoke-Api GET "/api/v1/admin/orders/$($order.order.id)/status-history" $null $admin.accessToken
if ($history.Count -ne 6) { throw 'Incomplete order status history' }
$schema = Invoke-Api GET '/v3/api-docs'
if (-not $schema.paths.'/api/v1/user/orders') { throw 'OpenAPI check failed' }
$null = Invoke-Api DELETE "/api/v1/admin/products/$($product.id)" $null $admin.accessToken
Write-Output "V1 smoke test passed: auth, SPU/SKU, address, cart, checkout, payment, fulfillment, cancellation, refund, inventory, reports, OpenAPI."
Write-Output "ProductId=$($product.id) SkuId=$($sku.id) CompletedOrderId=$($order.order.id)"
Write-Output 'Test account, category and orders remain in the database; test product is deactivated.'
