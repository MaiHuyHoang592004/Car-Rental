import { render, screen, waitFor } from "@testing-library/react";
import userEvent from "@testing-library/user-event";
import { beforeEach, describe, expect, it, vi } from "vitest";

const routerPush = vi.fn();

vi.mock("next/navigation", () => ({
  useRouter: () => ({ push: routerPush }),
}));

vi.mock("sonner", () => ({
  toast: {
    success: vi.fn(),
    error: vi.fn(),
  },
}));

vi.mock("./api", async (importOriginal) => {
  const actual = await importOriginal<typeof import("./api")>();
  return {
    ...actual,
    uploadTripPhoto: vi.fn(),
    createConditionReport: vi.fn(),
    checkInTrip: vi.fn(),
    checkOutTrip: vi.fn(),
  };
});

import {
  checkInTrip,
  checkOutTrip,
  createConditionReport,
  uploadTripPhoto,
} from "./api";
import { TripConditionForm } from "./trip-condition-form";

describe("TripConditionForm", () => {
  beforeEach(() => {
    vi.mocked(uploadTripPhoto).mockReset();
    vi.mocked(createConditionReport).mockReset();
    vi.mocked(checkInTrip).mockReset();
    vi.mocked(checkOutTrip).mockReset();
    routerPush.mockReset();
  });

  it("keeps submit disabled until required photo slots are uploaded", async () => {
    render(<TripConditionForm bookingId="bk-1" reportType="CHECK_IN" />);

    await userEvent.type(screen.getByLabelText(/Odometer/), "15000");

    expect(screen.getByRole("button", { name: /Hoan tat/ })).toBeDisabled();
    expect(screen.getByText(/Can du 4 anh bat buoc/)).toBeInTheDocument();
  });

  it("submits condition report before check-in when all required slots are ready", async () => {
    vi.mocked(uploadTripPhoto)
      .mockResolvedValueOnce("file-front")
      .mockResolvedValueOnce("file-rear")
      .mockResolvedValueOnce("file-left")
      .mockResolvedValueOnce("file-right");
    vi.mocked(createConditionReport).mockResolvedValueOnce({} as Awaited<ReturnType<typeof createConditionReport>>);
    vi.mocked(checkInTrip).mockResolvedValueOnce({} as Awaited<ReturnType<typeof checkInTrip>>);

    render(<TripConditionForm bookingId="bk-1" reportType="CHECK_IN" />);

    await userEvent.type(screen.getByLabelText(/Odometer/), "15000");
    await userEvent.clear(screen.getByLabelText(/Nhien lieu/));
    await userEvent.type(screen.getByLabelText(/Nhien lieu/), "80");
    await upload("Tai anh Mat truoc", "front.jpg");
    await upload("Tai anh Mat sau", "rear.jpg");
    await upload("Tai anh Ben trai", "left.jpg");
    await upload("Tai anh Ben phai", "right.jpg");

    await waitFor(() => expect(screen.getByRole("button", { name: /Hoan tat/ })).toBeEnabled());
    await userEvent.click(screen.getByRole("button", { name: /Hoan tat/ }));

    await waitFor(() => expect(createConditionReport).toHaveBeenCalled());
    expect(createConditionReport).toHaveBeenCalledWith(
      "bk-1",
      expect.objectContaining({
        reportType: "CHECK_IN",
        odometer: 15000,
        fuelLevel: 80,
        photos: [
          expect.objectContaining({ angle: "FRONT", fileId: "file-front" }),
          expect.objectContaining({ angle: "REAR", fileId: "file-rear" }),
          expect.objectContaining({ angle: "LEFT", fileId: "file-left" }),
          expect.objectContaining({ angle: "RIGHT", fileId: "file-right" }),
        ],
      }),
      expect.any(String),
    );
    expect(checkInTrip).toHaveBeenCalledWith("bk-1", {
      odometer: 15000,
      fuelLevel: 80,
      note: undefined,
    });
    expect(routerPush).toHaveBeenCalledWith("/bookings/bk-1");
  });

  it("shows upload error on the affected slot", async () => {
    vi.mocked(uploadTripPhoto).mockRejectedValueOnce(new Error("upload failed"));

    render(<TripConditionForm bookingId="bk-1" reportType="CHECK_OUT" />);
    await upload("Tai anh Mat truoc", "front.jpg");

    expect(await screen.findByText("Tai anh that bai")).toBeInTheDocument();
  });
});

async function upload(label: string, name: string) {
  const file = new File(["content"], name, { type: "image/jpeg" });
  await userEvent.upload(screen.getByLabelText(label), file);
}
