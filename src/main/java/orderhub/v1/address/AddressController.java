package orderhub.v1.address;

import jakarta.validation.Valid;
import java.util.List;
import orderhub.v1.common.Actor;
import orderhub.v1.address.entity.Address;
import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.*;
import io.swagger.v3.oas.annotations.tags.Tag;

@RestController @RequestMapping("/api/v1/user/addresses") @Tag(name="V1 收货地址")
public class AddressController {
    private final AddressService addresses;public AddressController(AddressService addresses){this.addresses=addresses;}
    @GetMapping public List<Address> list(){return addresses.list(Actor.current().id());}
    @PostMapping @ResponseStatus(HttpStatus.CREATED) public Address add(@Valid @RequestBody AddressInput in){return addresses.save(Actor.current().id(),null,in);}
    @PutMapping("/{id}") public Address edit(@PathVariable long id,@Valid @RequestBody AddressInput in){return addresses.save(Actor.current().id(),id,in);}
    @DeleteMapping("/{id}") @ResponseStatus(HttpStatus.NO_CONTENT) public void delete(@PathVariable long id){addresses.delete(Actor.current().id(),id);}
    @PutMapping("/{id}/default") @ResponseStatus(HttpStatus.NO_CONTENT) public void defaultAddress(@PathVariable long id){addresses.setDefault(Actor.current().id(),id);}
}
