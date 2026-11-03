package ik.am.jpetstore.order.web;

import ik.am.jpetstore.order.api.CheckoutCommand;
import ik.am.jpetstore.order.domain.CheckoutModels.CheckoutSnapshot;
import ik.am.jpetstore.order.domain.OrderModels.Address;
import ik.am.jpetstore.shared.error.BusinessFailure;

import jakarta.validation.Valid;

import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.authentication.AnonymousAuthenticationToken;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RestController;

/**
 * T300: 确认快照 REST适配。
 *
 * <p>控制器只做协议适配：读取身份与请求体、调用应用端口、映射为 DTO；事务在应用服务里，控制器
 * 不开事务（plan §5.5）。请求里没有价格、归属与明细字段，客户端无法伪造快照
 * （ORD-01、ORD-13、NFR-02）。</p>
 */
@RestController
public class CheckoutController {

    private static final String VERSION = "1.0";

    private final CheckoutCommand checkoutCommand;

    private final OrderAssembler assembler;

    public CheckoutController(CheckoutCommand checkoutCommand, OrderAssembler assembler) {
        this.checkoutCommand = checkoutCommand;
        this.assembler = assembler;
    }

    /** 创建确认快照：成功返回 201，快照绑定身份与购物车版本。 */
    @PostMapping(path = "/api/checkouts", version = VERSION)
    public ResponseEntity<OrderDtos.CheckoutResponseDto> create(Authentication authentication,
            @Valid @RequestBody OrderDtos.CreateCheckoutRequest request) {
        String userId = requireUsername(authentication);
        CheckoutSnapshot snapshot = checkoutCommand.create(new CheckoutCommand.CreateCheckoutRequest(userId,
                request.cartVersion(), toAddress(request.billingAddress()), toAddress(request.shippingAddress()),
                new CheckoutCommand.FakePayment(request.payment().cardType(), request.payment().cardNumber(),
                        request.payment().expiryDate())));
        return ResponseEntity.status(HttpStatus.CREATED).body(assembler.toCheckoutResponse(snapshot));
    }

    private Address toAddress(OrderDtos.AddressDto dto) {
        return new Address(dto.firstName(), dto.lastName(), dto.address1(), dto.address2(), dto.city(), dto.state(),
                dto.zip(), dto.country());
    }

    private String requireUsername(Authentication authentication) {
        if (authentication == null || !authentication.isAuthenticated()
                || authentication instanceof AnonymousAuthenticationToken) {
            throw new BusinessFailure(BusinessFailure.Code.AUTHENTICATION_REQUIRED, "需要登录");
        }
        return authentication.getName();
    }
}
