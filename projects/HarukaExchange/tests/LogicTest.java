import HarukaEdit.exchange.ExchangeRecipe;
import HarukaEdit.exchange.ItemUtil;
import org.bukkit.Material;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.PlayerInventory;

import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.Map;

public class LogicTest {
    static final class Stack extends ItemStack {
        final Material type;
        final String nbt;
        int amount;
        Stack(Material type, String nbt, int amount) { super(type); this.type=type; this.nbt=nbt; this.amount=amount; }
        public Material getType(){ return type; }
        public int getAmount(){ return amount; }
        public void setAmount(int a){ amount=a; }
        public int getMaxStackSize(){ return 64; }
        public Map<String,Object> serialize(){
            Map<String,Object> map=new LinkedHashMap<String,Object>();
            map.put("type", type.name()); map.put("amount",amount); map.put("forgeCapNBT",nbt); return map;
        }
        public Stack clone(){ return new Stack(type,nbt,amount); }
        public String toString(){ return type+":"+nbt+"x"+amount; }
    }
    static final class Inv implements PlayerInventory {
        final ItemStack[] content;
        Inv(int size){content=new ItemStack[size];}
        public org.bukkit.inventory.InventoryHolder getHolder(){return null;}
        public int getSize(){return content.length;}
        public ItemStack[] getContents(){return content.clone();}
        public void setItem(int slot, ItemStack item){content[slot]=item;}
        public ItemStack getItem(int slot){return content[slot];}
        public HashMap<Integer,ItemStack> addItem(ItemStack... items){return new HashMap<Integer,ItemStack>();}
    }
    static void check(boolean ok,String msg){if(!ok)throw new AssertionError(msg);}
    public static void main(String[] args){
        Stack a1=new Stack(Material.STONE,"A",5);
        Stack a2=new Stack(Material.STONE,"A",64);
        Stack b=new Stack(Material.STONE,"B",64);
        check(ItemUtil.sameExactItem(a1,a2),"amount should be ignored");
        check(!ItemUtil.sameExactItem(a1,b),"NBT should be compared");

        ExchangeRecipe r=new ExchangeRecipe("r");
        r.setInput1(new Stack(Material.STONE,"A",3));
        r.setInput2(new Stack(Material.STONE,"B",2));
        r.setOutput(new Stack(Material.STONE,"OUT",1));
        Inv inv=new Inv(6);
        inv.setItem(0,new Stack(Material.STONE,"A",10));
        inv.setItem(1,new Stack(Material.STONE,"B",7));
        check(ItemUtil.maxTrades(inv,r,64)==3,"distinct max trades");
        check(ItemUtil.removeForTrades(inv,r,2),"remove two trades");
        check(ItemUtil.count(inv,new Stack(Material.STONE,"A",1))==4,"A remaining");
        check(ItemUtil.count(inv,new Stack(Material.STONE,"B",1))==3,"B remaining");

        ExchangeRecipe same=new ExchangeRecipe("same");
        same.setInput1(new Stack(Material.STONE,"A",3));
        same.setInput2(new Stack(Material.STONE,"A",2));
        same.setOutput(new Stack(Material.STONE,"OUT",1));
        Inv inv2=new Inv(3);
        inv2.setItem(0,new Stack(Material.STONE,"A",11));
        check(ItemUtil.maxTrades(inv2,same,64)==2,"same-item requirements should combine");
        check(ItemUtil.removeForTrades(inv2,same,2),"remove same items");
        check(ItemUtil.count(inv2,new Stack(Material.STONE,"A",1))==1,"same remaining");
        System.out.println("LogicTest PASS");
    }
}
