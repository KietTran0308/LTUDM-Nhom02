package bus;

import dao.BillDAO;
import dto.BillDTO;
import java.util.List;

/** Dùng lại BillDAO/JDBC và luồng thanh toán gốc thay vì Spring BillService. */
public class KdsBillBUS {

    private final BillDAO billDAO = new BillDAO();
    private final ServerOrderBUS orderBUS = new ServerOrderBUS();
    public List<BillDTO> getAllBills() {return billDAO.getAllBills();}
    public BillDTO findById(long id) {return getAllBills().stream().filter(b -> b.getId()!=null && b.getId()==id).findFirst().orElse(null);}
    public BillDTO findByOrderId(long orderId) {return getAllBills().stream().filter(b -> b.getOrderId()!=null && b.getOrderId()==orderId).findFirst().orElse(null);}
    public boolean payTable(BillDTO bill,long tableId) {return orderBUS.processTablePayment(bill,tableId);}
}
