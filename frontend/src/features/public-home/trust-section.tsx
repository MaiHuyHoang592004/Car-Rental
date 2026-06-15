import { Headphones, ShieldCheck, Star, Verified } from "lucide-react";

const CUSTOMER_IMAGE =
  "https://lh3.googleusercontent.com/aida-public/AB6AXuB2ChjU2-ZOWsByUGDsdiB_0o-_8YVPw3JZI6iTBHkmhfMVaDSxaWwUZqFjT_JgwPvL76HDRPDgx6XWjygoiWNQ_65PvALsZPNptmN4H4ps522rmYp_FNxbOX4CBoj6NwcClQYD1n6-XwKA4FnfBBuxKd1y1UcHaTJiIrY5W4Ty1tKUahoB8bj5vkx6UvilyCBHovMcxm6uwNQyBtgn7oAXhsajfnZ2Ki0Xn1w9I5EcoTJfhpAusCfz1xDI5pJovwJzbDNqgYapelI";

const TRUST_ITEMS = [
  {
    icon: Verified,
    title: "Minh bạch tuyệt đối",
    description: "Thông tin xe, giá thuê và trạng thái đặt chỗ được hiển thị rõ ràng.",
    tone: "text-emerald-600 bg-emerald-50",
  },
  {
    icon: Headphones,
    title: "Hỗ trợ 24/7",
    description: "Đội ngũ hỗ trợ luôn đồng hành khi bạn cần xử lý thay đổi trong chuyến đi.",
    tone: "text-blue-600 bg-blue-50",
  },
  {
    icon: ShieldCheck,
    title: "An tâm mỗi hành trình",
    description: "Quy trình xác minh và chính sách chuyến đi giúp giảm rủi ro khi thuê xe.",
    tone: "text-amber-700 bg-amber-50",
  },
];

export function TrustSection() {
  return (
    <section className="relative left-1/2 w-screen -translate-x-1/2 overflow-x-hidden bg-white py-16 md:py-20">
      <div className="rf-shell-container grid gap-10 lg:grid-cols-2 lg:items-center">
        <div>
          <h2 className="text-3xl font-bold text-foreground">Tại sao nên chọn RentFlow?</h2>
          <div className="mt-7 space-y-5">
            {TRUST_ITEMS.map((item) => {
              const Icon = item.icon;
              return (
                <div key={item.title} className="flex gap-4">
                  <div className={`flex size-10 shrink-0 items-center justify-center rounded-xl ${item.tone}`}>
                    <Icon className="h-5 w-5" />
                  </div>
                  <div>
                    <h3 className="text-sm font-semibold text-foreground">{item.title}</h3>
                    <p className="mt-1 text-sm leading-6 text-muted-foreground">{item.description}</p>
                  </div>
                </div>
              );
            })}
          </div>
        </div>

        <figure className="rounded-[1.5rem] bg-[#e6e8ea] p-6 shadow-sm md:p-8">
          <div className="mb-5 flex gap-1 text-[#d97706]">
            {Array.from({ length: 5 }).map((_, index) => (
              <Star key={index} className="h-5 w-5 fill-current" />
            ))}
          </div>
          <blockquote className="text-base italic leading-7 text-foreground md:text-lg">
            &ldquo;Tôi thuê Mazda CX-5 cho chuyến đi gia đình. Xe mới, chủ xe phản hồi nhanh
            và quy trình nhận xe trên RentFlow rất gọn.&rdquo;
          </blockquote>
          <figcaption className="mt-7 flex items-center gap-4">
            <img
              src={CUSTOMER_IMAGE}
              alt="Nguyễn Minh Quân"
              className="size-12 rounded-full object-cover"
            />
            <div>
              <p className="text-sm font-semibold text-foreground">Nguyễn Minh Quân</p>
              <p className="text-xs text-muted-foreground">Khách hàng từ Hà Nội</p>
            </div>
          </figcaption>
        </figure>
      </div>
    </section>
  );
}
