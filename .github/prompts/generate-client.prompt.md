---
name: generate-client
description: "Generate typed API clients from OpenAPI contracts using the specified generator and configuration. Input contract path and output directory."
applyTo: "sprint-09-trading-ui"
---

# Generate Typed API Client

## Parameters

- **contract**: Path to OpenAPI contract file (e.g., `contracts/trade-api.yaml`)
- **output**: Output directory for generated code (e.g., `src/generated/trade-api`)
- **language**: Target language (typescript, java, etc.)

## What This Does

1. Runs the OpenAPI generator on the contract
2. Produces fully-typed client code with models and services
3. Outputs to the specified directory
4. Regenerates on every contract change

## Important Notes

- **Pin the generator version** in `package.json` or build config
- **Set `skipValidateSpec=true`** (contracts lack `info.license.identifier`, this is correct)
- **Never edit generated output by hand** — always regenerate
- **Commit generated output** to git (not gitignored)
- **Wrap awkward shapes** in services outside the generated tree

## Example: Generate Trade API Client

```bash
cd sprint-09-trading-ui

# Using OpenAPI Generator CLI (npm)
npx @openapitools/openapi-generator-cli generate \
  -i ../contracts/trade-api.yaml \
  -g typescript-angular \
  -o src/generated/trade-api \
  --skip-validate-spec

# Verify output
ls -la src/generated/trade-api/

# Commit the result
git add src/generated/trade-api/
git commit -m "chore: regenerate trade-api client from contract"
```

## Result

Generated directory contains:
- `api.service.ts` — HTTP service with all endpoints
- `models/` — TypeScript interfaces for all models
- `index.ts` — Public exports

You can now use:

```typescript
import { OrderService } from 'src/generated/trade-api/api.service';
import { Order, PlaceOrderRequest } from 'src/generated/trade-api/models';

// In your own service:
@Injectable()
export class OrderClient {
  constructor(private api: OrderService) {}
  
  placeOrder(req: PlaceOrderRequest): Observable<Order> {
    return this.api.placeOrder(req);
  }
}
```
