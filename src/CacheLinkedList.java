public class CacheLinkedList<E>{

    private ListNode root;
    private int size;

    public CacheLinkedList(){
        root = new ListNode(null,null,null);
        root.prev = root;
        root.next = root;
    }

    private ListNode nullIfRoot(ListNode node){
        return node==root?null:node;
    }

    public ListNode getFirst(){
        return nullIfRoot(root.next);
    }

    public ListNode getLast(){
        return nullIfRoot(root.prev);
    }

    public ListNode add(E val){
        ListNode toAdd = new ListNode(root.prev,val,root);
        root.prev.next = toAdd;
        root.prev = toAdd;
        size++;
        return toAdd;
    }

    public E remove(ListNode node){
        if(node==null){
            return null;
        }
        size--;
        node.prev.next = node.next;
        node.next.prev = node.prev;
        return node.val;
    }

    public int size(){
        return size;
    }

    public String toString(){
        StringBuilder returned = new StringBuilder("[");
        ListNode node = root.next;
        if(node!=root){
            while(node!=root){
                returned.append(node.val);
                returned.append(", ");
                node = node.next;
            }
            returned.delete(returned.length()-2,returned.length());
        }

        returned.append("]");
        return returned.toString();
    }

    public class ListNode{

        private ListNode prev, next;
        public E val;

        private ListNode(ListNode prev, E val, ListNode next){
            this.prev = prev;
            this.next = next;
            this.val = val;
        }

        public String toString(){
            return val.toString();
        }
    }

}
