param(
    [string]$BaseUrl = 'http://localhost:8080',
    [string]$AdminUsername = $env:ADMIN_USERNAME,
    [string]$AdminPassword = $env:ADMIN_PASSWORD
)
$ErrorActionPreference = 'Stop'
if (-not $AdminUsername -or -not $AdminPassword) {
    throw 'Set ADMIN_USERNAME and ADMIN_PASSWORD or pass parameters before running this smoke test.'
}

function Invoke-Api($Method, $Path, $Body, $Token) {
    $parameters = @{ Method = $Method; Uri = "$BaseUrl$Path" }
    if ($null -ne $Body) {
        $parameters.ContentType = 'application/json; charset=utf-8'
        $parameters.Body = [System.Text.Encoding]::UTF8.GetBytes(($Body | ConvertTo-Json -Depth 10))
    }
    if ($Token) { $parameters.Headers = @{ Authorization = "Bearer $Token" } }
    Invoke-RestMethod @parameters
}

$suffix = [Guid]::NewGuid().ToString('N').Substring(0, 12)
$username = "smoke_$suffix"
$password = [Guid]::NewGuid().ToString('N')
$admin = Invoke-Api POST '/api/auth/login' @{ username = $AdminUsername; password = $AdminPassword }
$product = Invoke-Api POST '/api/admin/products' @{
    sku = "SMOKE-$suffix"; name = 'Smoke keyboard'; description = 'Deployment verification'
    category = 'smoke'; price = 49.90; active = $true; stock = 5
} $admin.accessToken
$null = Invoke-Api POST '/api/auth/register' @{ username = $username; password = $password }
$user = Invoke-Api POST '/api/auth/login' @{ username = $username; password = $password }
$null = Invoke-Api GET "/api/products/$($product.id)"
$null = Invoke-Api PUT "/api/cart/items/$($product.id)" @{ quantity = 2 } $user.accessToken
$key = "smoke-$suffix"
$order = Invoke-Api POST '/api/orders' @{ requestKey = $key } $user.accessToken
$retry = Invoke-Api POST '/api/orders' @{ requestKey = $key } $user.accessToken
if ($order.id -ne $retry.id -or $order.totalAmount -ne 99.8) { throw 'Order total or idempotency check failed' }
$stock = Invoke-Api GET "/api/admin/products/$($product.id)/stock" $null $admin.accessToken
if ($stock.quantity -ne 3) { throw 'Stock deduction check failed' }
$null = Invoke-Api POST "/api/orders/$($order.id)/cancel" $null $user.accessToken
$null = Invoke-Api POST "/api/orders/$($order.id)/cancel" $null $user.accessToken
$stock = Invoke-Api GET "/api/admin/products/$($product.id)/stock" $null $admin.accessToken
if ($stock.quantity -ne 5) { throw 'Cancellation idempotency check failed' }
$null = Invoke-Api DELETE "/api/admin/products/$($product.id)" $null $admin.accessToken
$schema = Invoke-Api GET '/v3/api-docs'
if (-not $schema.paths.'/api/orders') { throw 'OpenAPI check failed' }
Write-Output "Smoke test passed: auth, product, checkout, retry, stock, cancellation, OpenAPI. ProductId=$($product.id)"
Write-Output 'Test user and cancelled order remain in the database; test product is deactivated.'
