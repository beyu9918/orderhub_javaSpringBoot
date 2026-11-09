package orderhub.v1.address;

import com.baomidou.mybatisplus.core.conditions.query.QueryWrapper;
import com.baomidou.mybatisplus.core.conditions.update.UpdateWrapper;
import java.util.List;
import orderhub.api.ApiException;
import orderhub.v1.address.entity.Address;
import orderhub.v1.address.mapper.AddressMapper;
import orderhub.v1.user.mapper.AccountMapper;
import orderhub.v1.common.Db;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class AddressService {
    private final AddressMapper addresses;private final AccountMapper users;
    public AddressService(AddressMapper addresses,AccountMapper users){this.addresses=addresses;this.users=users;}
    public List<Address> list(long user){return addresses.selectList(new QueryWrapper<Address>().eq("user_id",user).orderByDesc("is_default").orderByAsc("id"));}
    public Address owned(long user,long id){
        return Db.require(addresses.selectOne(new QueryWrapper<Address>().eq("id",id).eq("user_id",user)));
    }
    @Transactional public Address save(long user,Long id,AddressInput in){
        Db.lock(users,user);Address a=id==null?new Address():owned(user,id);
        if(id==null)Db.check(addresses.selectCount(new QueryWrapper<Address>().eq("user_id",user))<20,"最多保存 20 个收货地址");
        if(in.isDefault()) clearDefault(user);
        a.userId=user;a.receiverName=in.receiverName();a.phone=in.phone();a.province=in.province();a.city=in.city();
        a.district=in.district();a.detailAddress=in.detailAddress();a.postalCode=in.postalCode();a.isDefault=in.isDefault();a.defaultSlot=in.isDefault()?1:null;a.updatedAt=Db.now();
        if(id==null)addresses.insert(a);else addresses.updateById(a);
        return addresses.selectById(a.id);
    }
    @Transactional public void setDefault(long user,long id){
        Db.lock(users,user);owned(user,id);clearDefault(user);
        addresses.update(null,new UpdateWrapper<Address>().eq("id",id).set("is_default",true).set("default_slot",1).set("updated_at",Db.now()));
    }
    @Transactional public void delete(long user,long id){
        Db.lock(users,user);owned(user,id);
        // Orders keep an immutable address snapshot and no longer need the live address pointer.
        addresses.deleteById(id);
    }
    private void clearDefault(long user){
        addresses.update(null,new UpdateWrapper<Address>().eq("user_id",user).eq("is_default",true)
            .set("is_default",false).set("default_slot",null).set("updated_at",Db.now()));
    }
}
